package com.work.mautonlaundry.payments.gateway.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

abstract class AbstractSandboxGatewayAdapter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected Map<String, Object> parsePayload(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid webhook payload");
        }
    }

    protected String getHeader(Map<String, String> headers, String key) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    protected PaymentStatus mapStatus(String raw) {
        if (raw == null) {
            return PaymentStatus.FAILED;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("SUCCESS") || normalized.contains("COMPLETE") || normalized.contains("PAID")) {
            return PaymentStatus.COMPLETED;
        }
        if (normalized.contains("PENDING") || normalized.contains("INIT")) {
            return PaymentStatus.PENDING;
        }
        if (normalized.contains("REFUND")) {
            return PaymentStatus.REFUNDED;
        }
        return PaymentStatus.FAILED;
    }

    protected void requireSignature(Map<String, String> headers, String signatureHeaderName) {
        String signature = getHeader(headers, signatureHeaderName);
        if (signature == null || signature.isBlank()) {
            throw new ForbiddenOperationException("Missing webhook signature");
        }
    }

    protected void requireExpectedSignature(Map<String, String> headers, String signatureHeaderName, String expectedSecret, String providerName) {
        requireSignature(headers, signatureHeaderName);
        if (expectedSecret == null || expectedSecret.isBlank()) {
            return;
        }
        String signature = getHeader(headers, signatureHeaderName);
        if (!expectedSecret.equals(signature)) {
            throw new ForbiddenOperationException("Invalid webhook signature for " + providerName);
        }
    }

    protected String hmacSha512Hex(String payload, String secretKey, boolean upperCase) {
        try {
            if (secretKey == null || secretKey.isBlank()) {
                throw new ForbiddenOperationException("Missing provider secret key for webhook validation");
            }
            String safePayload = payload == null ? "" : payload;
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(safePayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            String value = builder.toString();
            return upperCase ? value.toUpperCase(Locale.ROOT) : value;
        } catch (ForbiddenOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ForbiddenOperationException("Unable to validate webhook signature");
        }
    }

    protected void verifyHmacSignature(Map<String, String> headers, String signatureHeaderName, String payload, String secretKey, boolean uppercaseExpected, String providerName) {
        requireSignature(headers, signatureHeaderName);
        String provided = getHeader(headers, signatureHeaderName);
        String expected = hmacSha512Hex(payload, secretKey, uppercaseExpected);
        if (provided == null || !provided.trim().equals(expected)) {
            throw new ForbiddenOperationException("Invalid webhook signature for " + providerName);
        }
    }
}

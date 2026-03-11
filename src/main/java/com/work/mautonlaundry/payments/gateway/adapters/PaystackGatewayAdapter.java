package com.work.mautonlaundry.payments.gateway.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import com.work.mautonlaundry.payments.gateway.PaymentGatewayAdapter;
import com.work.mautonlaundry.payments.gateway.model.GatewayInitiationResult;
import com.work.mautonlaundry.payments.gateway.model.GatewayWebhookEvent;
import com.work.mautonlaundry.services.payments.PaymentGatewayProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PaystackGatewayAdapter extends AbstractSandboxGatewayAdapter implements PaymentGatewayAdapter {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final PaymentGatewayProperties properties;
    private final String appBaseUrl;

    public PaystackGatewayAdapter(
            PaymentGatewayProperties properties,
            @Value("${app.base-url:http://localhost:8079}") String appBaseUrl
    ) {
        this.properties = properties;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYSTACK;
    }

    @Override
    public GatewayInitiationResult initiatePayment(Payment payment) {
        try {
            String secretKey = properties.getPaystack().getSecretKey();
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalArgumentException("Paystack secret key is not configured");
            }

            String configuredBaseUrl = properties.getPaystack().getBaseUrl();
            String baseUrl = (configuredBaseUrl == null || configuredBaseUrl.isBlank())
                    ? "https://api.paystack.co"
                    : configuredBaseUrl;
            String reference = "PST-" + UUID.randomUUID();

            Map<String, Object> payload = new HashMap<>();
            payload.put("email", payment.getBooking().getUser().getEmail());
            payload.put("amount", toKobo(payment.getAmount()));
            payload.put("reference", reference);
            payload.put("callback_url", appBaseUrl + "/api/v1/payments/callback/paystack");

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/transaction/initialize"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Paystack initialize failed with status " + response.statusCode());
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object statusObj = responseMap.get("status");
            if (!(statusObj instanceof Boolean) || !((Boolean) statusObj)) {
                throw new IllegalStateException("Paystack initialize rejected request");
            }

            Object dataObj = responseMap.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                throw new IllegalStateException("Invalid Paystack response payload");
            }

            String authUrl = data.get("authorization_url") == null ? null : data.get("authorization_url").toString();
            String accessCode = data.get("access_code") == null ? null : data.get("access_code").toString();
            String returnedReference = data.get("reference") == null ? reference : data.get("reference").toString();

            return new GatewayInitiationResult(returnedReference, authUrl, accessCode, response.body());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize Paystack transaction", ex);
        }
    }

    @Override
    public GatewayWebhookEvent verifyAndParseWebhook(Map<String, String> headers, String payload) {
        verifyHmacSignature(
                headers,
                "x-paystack-signature",
                payload,
                properties.getPaystack().getSecretKey(),
                false,
                "PAYSTACK"
        );
        Map<String, Object> body = parsePayload(payload);
        String eventId = getHeader(headers, "x-event-id");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        Object data = body.get("data");
        String reference = null;
        String status = null;
        if (data instanceof Map<?, ?> dataMap) {
            Object refObj = dataMap.get("reference");
            Object statusObj = dataMap.get("status");
            reference = refObj == null ? null : refObj.toString();
            status = statusObj == null ? null : statusObj.toString();
        }
        return new GatewayWebhookEvent(eventId, reference, mapStatus(status), payload);
    }

    private long toKobo(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}

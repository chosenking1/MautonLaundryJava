package com.work.mautonlaundry.payments.gateway.adapters;

import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import com.work.mautonlaundry.payments.gateway.PaymentGatewayAdapter;
import com.work.mautonlaundry.payments.gateway.model.GatewayInitiationResult;
import com.work.mautonlaundry.payments.gateway.model.GatewayWebhookEvent;
import com.work.mautonlaundry.services.payments.PaymentGatewayProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class MonnifyGatewayAdapter extends AbstractSandboxGatewayAdapter implements PaymentGatewayAdapter {
    private final PaymentGatewayProperties properties;

    public MonnifyGatewayAdapter(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MONNIFY;
    }

    @Override
    public GatewayInitiationResult initiatePayment(Payment payment) {
        // Draft sandbox stub. Replace with Monnify API call + response mapping.
        String reference = "MON-" + UUID.randomUUID();
        String configuredBaseUrl = properties.getMonnify().getBaseUrl();
        String baseUrl = (configuredBaseUrl == null || configuredBaseUrl.isBlank())
                ? "https://sandbox.monnify.com"
                : configuredBaseUrl;
        String checkoutUrl = baseUrl + "/checkout/" + reference;
        String rawPayload = "{\"provider\":\"MONNIFY\",\"reference\":\"" + reference + "\",\"merchantId\":\""
                + properties.getMonnify().getMerchantId() + "\"}";
        return new GatewayInitiationResult(reference, checkoutUrl, null, rawPayload);
    }

    @Override
    public GatewayWebhookEvent verifyAndParseWebhook(Map<String, String> headers, String payload) {
        requireExpectedSignature(headers, "Monnify-Signature", properties.getMonnify().getWebhookSecret(), "MONNIFY");
        Map<String, Object> body = parsePayload(payload);
        String eventId = getHeader(headers, "Monnify-Event-Id");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        String reference = String.valueOf(body.getOrDefault("paymentReference", body.get("transactionReference")));
        String status = String.valueOf(body.getOrDefault("paymentStatus", body.get("status")));
        return new GatewayWebhookEvent(eventId, reference, mapStatus(status), payload);
    }
}

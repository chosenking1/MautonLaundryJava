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
public class SquadGatewayAdapter extends AbstractSandboxGatewayAdapter implements PaymentGatewayAdapter {
    private final PaymentGatewayProperties properties;

    public SquadGatewayAdapter(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.SQUAD;
    }

    @Override
    public GatewayInitiationResult initiatePayment(Payment payment) {
        String reference = "SQD-" + UUID.randomUUID();
        String configuredBaseUrl = properties.getSquad().getBaseUrl();
        String baseUrl = (configuredBaseUrl == null || configuredBaseUrl.isBlank())
                ? "https://sandbox.squadco.com"
                : configuredBaseUrl;
        String checkoutUrl = baseUrl + "/pay/" + reference;
        String rawPayload = "{\"provider\":\"SQUAD\",\"reference\":\"" + reference + "\",\"merchantId\":\""
                + properties.getSquad().getMerchantId() + "\"}";
        return new GatewayInitiationResult(reference, checkoutUrl, null, rawPayload);
    }

    @Override
    public GatewayWebhookEvent verifyAndParseWebhook(Map<String, String> headers, String payload) {
        verifyHmacSignature(
                headers,
                "x-squad-encrypted-body",
                payload,
                properties.getSquad().getSecretKey(),
                true,
                "SQUAD"
        );
        Map<String, Object> body = parsePayload(payload);
        String eventId = getHeader(headers, "Squad-Event-Id");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        String reference = String.valueOf(body.getOrDefault("transaction_ref", body.get("reference")));
        String status = String.valueOf(body.getOrDefault("transaction_status", body.get("status")));
        return new GatewayWebhookEvent(eventId, reference, mapStatus(status), payload);
    }
}

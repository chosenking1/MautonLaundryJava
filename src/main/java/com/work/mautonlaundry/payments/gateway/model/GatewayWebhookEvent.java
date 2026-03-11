package com.work.mautonlaundry.payments.gateway.model;

import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GatewayWebhookEvent {
    private String eventId;
    private String gatewayReference;
    private PaymentStatus status;
    private String rawPayload;
}

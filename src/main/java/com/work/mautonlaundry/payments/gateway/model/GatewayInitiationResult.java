package com.work.mautonlaundry.payments.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GatewayInitiationResult {
    private String gatewayReference;
    private String checkoutUrl;
    private String accessCode;
    private String rawPayload;
}

package com.work.mautonlaundry.dtos.responses.paymentresponses;

import lombok.Data;

@Data
public class GatewayPaymentInitiationResponse {
    private Long paymentId;
    private String bookingId;
    private String provider;
    private String paymentStatus;
    private String gatewayReference;
    private String accessCode;
    private String checkoutUrl;
    private String message;
}

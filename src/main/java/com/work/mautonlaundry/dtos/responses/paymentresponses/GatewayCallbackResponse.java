package com.work.mautonlaundry.dtos.responses.paymentresponses;

import lombok.Data;

@Data
public class GatewayCallbackResponse {
    private String message;
    private String provider;
    private String reference;
}

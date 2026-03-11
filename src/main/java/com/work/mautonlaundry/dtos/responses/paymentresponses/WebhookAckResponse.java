package com.work.mautonlaundry.dtos.responses.paymentresponses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookAckResponse {
    private String message;
}

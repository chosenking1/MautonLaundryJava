package com.work.mautonlaundry.dtos.requests.deliveryrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptDeliveryJobRequest {
    @NotBlank(message = "bookingId is required")
    private String bookingId;

    @NotBlank(message = "agentId is required")
    private String agentId;

    @NotBlank(message = "phase is required")
    private String phase;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}

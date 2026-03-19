package com.work.mautonlaundry.dtos.requests.deliveryrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDeliveryStatusRequest {
    @NotBlank(message = "phase is required")
    private String phase;

    @NotBlank(message = "status is required")
    private String status;
}

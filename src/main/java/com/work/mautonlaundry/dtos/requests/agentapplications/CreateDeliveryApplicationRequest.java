package com.work.mautonlaundry.dtos.requests.agentapplications;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDeliveryApplicationRequest {
    @NotBlank(message = "Address ID is required")
    private String addressId;
}

package com.work.mautonlaundry.dtos.requests.pricingrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateServicePricingRequest {
    @NotBlank(message = "Price type is required")
    private String priceType;
    
    @NotBlank(message = "Item type is required")
    private String itemType;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private String description; // Maps to unit field
}
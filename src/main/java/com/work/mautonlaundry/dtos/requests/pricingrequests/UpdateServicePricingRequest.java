package com.work.mautonlaundry.dtos.requests.pricingrequests;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateServicePricingRequest {
    private String priceType;
    private String itemType;
    
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private String description; // Maps to unit field
}
package com.work.mautonlaundry.dtos.requests.laundryitemrequests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LaundryItemRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Unit is required")
    private String unit;
    
    @NotNull(message = "Base price for colored items is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal basePriceColored;
    
    @NotNull(message = "Base price for white items is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal basePriceWhite;
}
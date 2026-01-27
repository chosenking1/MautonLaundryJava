package com.work.mautonlaundry.dtos.requests.pricingrequests;

import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CalculatePriceRequest {
    
    @NotEmpty(message = "Items are required")
    @Valid
    private List<BookingItemRequest> items;
    
    private Boolean express = false;
    
    private BigDecimal deliveryDistance = BigDecimal.ZERO;
}
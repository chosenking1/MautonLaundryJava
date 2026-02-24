package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingItemRequest {
    @NotNull
    private Long serviceId;  // ID of the service (e.g., laundry, house cleaning)
    
    @NotNull
    private String itemType; // e.g., "SHIRT_WHITE", "OFFICE_CLEANING", "DEEP_CLEAN"
    
    @Min(1)
    private Integer quantity;
}
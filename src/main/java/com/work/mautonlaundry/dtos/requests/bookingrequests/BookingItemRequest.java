package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingItemRequest {
    @NotNull
    private Long itemId;
    
    @Min(1)
    private Integer quantity;
    
    @NotNull
    private String colorType;
}
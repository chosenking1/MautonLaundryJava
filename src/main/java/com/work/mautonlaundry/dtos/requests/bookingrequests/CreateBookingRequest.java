package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {
    @NotBlank
    private String bookingType;
    
    @NotBlank // Changed from @NotNull to @NotBlank for String ID
    private String pickupAddressId; // Changed type from Long to String
    
    private Boolean express = false;
    
    @NotEmpty
    @Valid
    private List<BookingItemRequest> items;
}
package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateBookingRequest {
    @NotBlank
    private String bookingType;
    
    @NotBlank // Changed from @NotNull to @NotBlank for String ID
    private String pickupAddressId; // Changed type from Long to String
    
    private Boolean express = false;

    /**
     * When the customer wants collecting. Both null means "as soon as
     * possible", which is what every already-released app version sends and
     * what the booking flow did before scheduling existed -- so old clients
     * keep working untouched. Supplying one without the other is refused; see
     * PickupSchedulingService.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate scheduledPickupDate;

    private String pickupSlotId;
    
    @NotEmpty
    @Valid
    private List<BookingItemRequest> items;

    private String discountCode;
}
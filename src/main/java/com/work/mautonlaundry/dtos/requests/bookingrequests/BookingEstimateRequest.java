package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BookingEstimateRequest {
    private Boolean express = false;

    @NotEmpty
    @Valid
    private List<BookingItemRequest> items;
}

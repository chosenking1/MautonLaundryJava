package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateBookingStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
}
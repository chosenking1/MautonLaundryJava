package com.work.mautonlaundry.dtos.requests.deliveryrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForceDispatchJobRequest {
    @NotBlank(message = "bookingId is required")
    private String bookingId;

    private String phase;
}

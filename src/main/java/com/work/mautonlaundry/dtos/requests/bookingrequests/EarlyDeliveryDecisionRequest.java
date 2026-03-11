package com.work.mautonlaundry.dtos.requests.bookingrequests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EarlyDeliveryDecisionRequest {
    @NotNull(message = "acceptTomorrow is required")
    private Boolean acceptTomorrow;
}

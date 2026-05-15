package com.work.mautonlaundry.dtos.requests.agentrequests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class AgentAvailabilityLocationRequest {
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;
    private Instant timestamp;
}

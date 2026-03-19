package com.work.mautonlaundry.dtos.requests.agentrequests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentAvailabilityLocationRequest {
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;
    private Long timestamp;
}

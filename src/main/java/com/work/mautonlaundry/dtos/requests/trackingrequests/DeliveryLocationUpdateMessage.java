package com.work.mautonlaundry.dtos.requests.trackingrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryLocationUpdateMessage {
    @NotBlank(message = "agentId is required")
    private String agentId;

    @NotBlank(message = "bookingId is required")
    private String bookingId;

    @NotNull(message = "latitude is required")
    private Double latitude;

    @NotNull(message = "longitude is required")
    private Double longitude;

    @NotNull(message = "timestamp is required")
    private Long timestamp;
}

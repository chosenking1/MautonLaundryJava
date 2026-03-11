package com.work.mautonlaundry.dtos.responses.trackingresponses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackingUpdatePayload {
    private Double lat;
    private Double lng;
}

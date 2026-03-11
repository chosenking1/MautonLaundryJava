package com.work.mautonlaundry.dtos.responses.trackingresponses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveLocationResponse {
    private Double lat;
    private Double lng;
    private Long updatedAt;
}

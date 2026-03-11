package com.work.mautonlaundry.dtos.requests.locationrequests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCurrentLocationRequest {
    @NotNull(message = "Latitude is required")
    private BigDecimal latitude;
    
    @NotNull(message = "Longitude is required")
    private BigDecimal longitude;
    
    private Integer bearing;
    
    private Double speed;
}

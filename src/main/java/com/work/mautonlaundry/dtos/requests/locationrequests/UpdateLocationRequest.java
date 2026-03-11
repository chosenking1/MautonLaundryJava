package com.work.mautonlaundry.dtos.requests.locationrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateLocationRequest {
    @NotBlank(message = "Booking ID is required")
    private String bookingId;
    
    @NotNull(message = "Latitude is required")
    private BigDecimal latitude;
    
    @NotNull(message = "Longitude is required")
    private BigDecimal longitude;
    
    private Integer bearing;
    
    private Double speed;
}

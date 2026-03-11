package com.work.mautonlaundry.dtos.responses.locationresponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLocationResponse {
    private String bookingId;
    private String deliveryAgentId;
    private String deliveryAgentName;
    private String deliveryAgentPhone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime lastUpdate;
    private Integer bearing;
    private Double speed;
    private Double distanceToCustomerKm;
    private String estimatedArrival;
    private String status;
    private String phase;
}

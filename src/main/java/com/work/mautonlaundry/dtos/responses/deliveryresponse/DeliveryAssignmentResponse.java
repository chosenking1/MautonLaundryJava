package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class DeliveryAssignmentResponse {
    private Long assignmentId;
    private String bookingId;
    private String trackingNumber;
    private String deliveryAgentId;
    private String deliveryAgentName;
    private String phase;
    private String status;
    private LocalDateTime offeredAt;
    private LocalDateTime respondedAt;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;
    private LocalDateTime lastLocationUpdate;
}

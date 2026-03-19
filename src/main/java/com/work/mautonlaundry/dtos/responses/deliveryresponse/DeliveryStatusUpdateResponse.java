package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryStatusUpdateResponse {
    private String bookingId;
    private String phase;
    private String routeStatus;
    private String assignmentStatus;
    private String bookingStatus;
    private DeliveryNextStop nextStop;
}

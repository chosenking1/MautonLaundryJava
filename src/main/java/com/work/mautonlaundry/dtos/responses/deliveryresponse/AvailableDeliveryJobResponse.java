package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvailableDeliveryJobResponse {
    private String bookingId;
    private DeliveryAssignmentPhase phase;
    private double pickupLat;
    private double pickupLng;
    private double distanceKm;
    private double radiusKm;
    private long createdAt;
}

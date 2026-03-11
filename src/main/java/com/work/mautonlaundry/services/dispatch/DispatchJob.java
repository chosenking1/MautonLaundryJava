package com.work.mautonlaundry.services.dispatch;

import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;

public record DispatchJob(
        String bookingId,
        DeliveryAssignmentPhase phase,
        double pickupLat,
        double pickupLng,
        int radiusIndex,
        long nextAttemptAt,
        long createdAt
) {
}

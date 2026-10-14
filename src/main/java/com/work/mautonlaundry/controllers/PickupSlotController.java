package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.pickup.PickupAvailabilityResponse;
import com.work.mautonlaundry.services.PickupSchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the customer may choose from on the Schedule step.
 *
 * <p>Computed here rather than in the app so the rules -- cutoff, lead time,
 * notice -- have one home. An app that decides for itself drifts from the
 * server the first time a rule changes, and the customer meets that drift as a
 * window they were offered and then refused.
 */
@RestController
@RequestMapping("/api/v1/bookings/pickup-availability")
@RequiredArgsConstructor
public class PickupSlotController {

    private final PickupSchedulingService pickupSchedulingService;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('BOOKING_CREATE')")
    public ResponseEntity<PickupAvailabilityResponse> availability() {
        return ResponseEntity.ok(pickupSchedulingService.availability());
    }
}

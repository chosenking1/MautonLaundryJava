package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.UpdateDeliveryAssignmentStatusRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryAssignmentResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryRouteResponse;
import com.work.mautonlaundry.services.DeliveryAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @GetMapping("/offers")
    @PreAuthorize("hasAuthority('DELIVERY_READ')")
    public ResponseEntity<List<DeliveryAssignmentResponse>> getMyOffers() {
        return ResponseEntity.ok(deliveryAssignmentService.getMyOffers());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('DELIVERY_READ')")
    public ResponseEntity<List<DeliveryAssignmentResponse>> getMyActiveAssignments() {
        return ResponseEntity.ok(deliveryAssignmentService.getMyActiveAssignments());
    }

    @GetMapping("/bookings/{bookingId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<List<DeliveryAssignmentResponse>> getBookingAssignments(@PathVariable String bookingId) {
        return ResponseEntity.ok(deliveryAssignmentService.getAssignmentsForBooking(bookingId));
    }

    @GetMapping("/bookings/{bookingId}/route")
    @PreAuthorize("hasAuthority('DELIVERY_READ')")
    public ResponseEntity<DeliveryRouteResponse> getDeliveryRoute(@PathVariable String bookingId) {
        return ResponseEntity.ok(deliveryAssignmentService.getDeliveryRoute(bookingId));
    }

    @PostMapping("/bookings/{bookingId}/accept")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<MessageResponse> acceptOffer(@PathVariable String bookingId) {
        boolean accepted = deliveryAssignmentService.acceptOffer(bookingId);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse("Delivery offer could not be accepted"));
        }
        return ResponseEntity.ok(new MessageResponse("Delivery offer accepted"));
    }

    @PostMapping("/bookings/{bookingId}/decline")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<MessageResponse> declineOffer(@PathVariable String bookingId) {
        boolean declined = deliveryAssignmentService.declineOffer(bookingId);
        if (!declined) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse("Delivery offer could not be declined"));
        }
        return ResponseEntity.ok(new MessageResponse("Delivery offer declined"));
    }

    @PatchMapping("/assignments/{assignmentId}/status")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<DeliveryAssignmentResponse> updateAssignmentStatus(
            @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateDeliveryAssignmentStatusRequest request) {
        DeliveryAssignmentStatus status;
        try {
            status = DeliveryAssignmentStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid delivery assignment status: " + request.getStatus());
        }
        return ResponseEntity.ok(deliveryAssignmentService.updateAssignmentStatus(assignmentId, status));
    }
}

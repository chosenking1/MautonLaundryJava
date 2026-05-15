package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.dtos.responses.trackingresponses.LiveLocationResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.TrackingService;
import com.work.mautonlaundry.services.tracking.LiveLocationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {
    private final TrackingService trackingService;
    private final BookingRepository bookingRepository;

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<LiveLocationResponse> getLatestLocation(@PathVariable String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        boolean isOwner = booking.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.hasRole("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ForbiddenOperationException("You are not authorized to view this delivery location");
        }

        Optional<LiveLocationSnapshot> snapshot = trackingService.getLatestLocationForBooking(bookingId);
        if (snapshot.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LiveLocationSnapshot value = snapshot.get();
        LiveLocationResponse response = LiveLocationResponse.builder()
                .lat(value.lat())
                .lng(value.lng())
                .updatedAt(value.updatedAt())
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Rider posts each GPS sample here while a delivery is active. Body:
     * {@code {"latitude": <double>, "longitude": <double>, "timestamp": <epoch-seconds, optional>}}.
     * Replaces the STOMP SEND path on the rider side — see TrackingService.handleHttpLocationUpdate.
     */
    @PostMapping("/{bookingId}/location")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<MessageResponse> postLocation(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        Object latRaw = body.get("latitude");
        Object lngRaw = body.get("longitude");
        if (!(latRaw instanceof Number) || !(lngRaw instanceof Number)) {
            return ResponseEntity.badRequest().body(new MessageResponse("latitude and longitude are required"));
        }
        double latitude = ((Number) latRaw).doubleValue();
        double longitude = ((Number) lngRaw).doubleValue();
        Long timestamp = body.get("timestamp") instanceof Number
                ? ((Number) body.get("timestamp")).longValue()
                : null;
        trackingService.handleHttpLocationUpdate(currentUser, bookingId, latitude, longitude, timestamp);
        return ResponseEntity.ok(new MessageResponse("Location recorded"));
    }
}

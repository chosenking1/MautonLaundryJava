package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingEstimateRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingEstimateResponse;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingStatusRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<CreateBookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CreateBookingResponse response = bookingService.createBooking(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/estimate")
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<BookingEstimateResponse> estimateBooking(
            @Valid @RequestBody BookingEstimateRequest request) {
        return ResponseEntity.ok(bookingService.estimateBooking(request));
    }

    @GetMapping
    public ResponseEntity<Page<BookingDetailsResponse>> getUserBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<BookingDetailsResponse> bookings;
        if (status != null && !status.isBlank()) {
            List<BookingStatus> statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return BookingStatus.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Invalid status: " + s);
                        }
                    })
                    .toList();
            bookings = bookingService.getUserBookingsByStatuses(pageable, statuses);
        } else {
            bookings = bookingService.getUserBookings(pageable);
        }
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<BookingDetailsResponse> getBooking(@PathVariable String bookingId) {
        BookingDetailsResponse response = bookingService.getBookingDetails(bookingId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingId}/status")
    @PreAuthorize("hasAuthority('BOOKING_UPDATE')")
    public ResponseEntity<MessageResponse> updateBookingStatus(
            @PathVariable String bookingId,
            @Valid @RequestBody UpdateBookingStatusRequest request) {
        BookingStatus status = parseBookingStatus(request.getStatus());
        
        bookingService.updateBookingStatus(bookingId, status);
        
        return ResponseEntity.ok(new MessageResponse("Status updated successfully"));
    }

    private BookingStatus parseBookingStatus(String rawStatus) {
        String statusStr = rawStatus == null ? null : rawStatus.trim().toUpperCase();
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Invalid booking status: " + rawStatus);
        }

        try {
            return BookingStatus.valueOf(statusStr);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid booking status: " + rawStatus + ". Allowed values: " +
                            java.util.Arrays.toString(BookingStatus.values())
            );
        }
    }

    @PostMapping("/{bookingId}/laundry/received")
    @PreAuthorize("hasAuthority('BOOKING_UPDATE')")
    public ResponseEntity<MessageResponse> markLaundryReceived(@PathVariable String bookingId) {
        bookingService.markLaundryReceived(bookingId);
        return ResponseEntity.ok(new MessageResponse("Booking received by laundry"));
    }

    @PostMapping("/{bookingId}/laundry/accept")
    @PreAuthorize("hasAuthority('BOOKING_UPDATE')")
    public ResponseEntity<MessageResponse> acceptLaundryAssignment(@PathVariable String bookingId) {
        bookingService.acceptLaundryAssignment(bookingId);
        return ResponseEntity.ok(new MessageResponse("Laundry assignment accepted"));
    }

    @PostMapping("/{bookingId}/laundry/completed")
    @PreAuthorize("hasAuthority('BOOKING_UPDATE')")
    public ResponseEntity<MessageResponse> markLaundryCompleted(@PathVariable String bookingId) {
        bookingService.markLaundryCompleted(bookingId);
        return ResponseEntity.ok(new MessageResponse("Laundry completed and delivery dispatch queued"));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<MessageResponse> cancelBooking(@PathVariable String bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(new MessageResponse("Booking cancelled successfully"));
    }

    @PostMapping("/{bookingId}/delivery/early-response")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<MessageResponse> respondToEarlyDelivery(
            @PathVariable String bookingId,
            @RequestBody Map<String, Boolean> body) {
        Boolean acceptTomorrow = body.get("acceptTomorrow");
        if (acceptTomorrow == null) {
            throw new IllegalArgumentException("acceptTomorrow field is required");
        }
        bookingService.respondToEarlyDelivery(bookingId, acceptTomorrow);
        return ResponseEntity.ok(new MessageResponse(
                acceptTomorrow ? "You accepted early delivery for tomorrow" : "You declined early delivery"
        ));
    }

    @GetMapping("/{bookingId}/coordinates")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<Map<String, Double>> getDeliveryCoordinates(@PathVariable String bookingId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        boolean isOwner = booking.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.hasRole("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ForbiddenOperationException("You are not authorized to view this booking");
        }
        
        java.util.Map<String, Double> coordinates = bookingService.getDeliveryCoordinates(bookingId);
        if (coordinates == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(coordinates);
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.EarlyDeliveryDecisionRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingStatusRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<CreateBookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<BookingDetailsResponse>> getUserBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BookingDetailsResponse> bookings = bookingService.getUserBookings(pageable);
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

        return switch (statusStr) {
            case "ASSIGNED" -> BookingStatus.PENDING_LAUNDRYMAN_ACCEPTANCE;
            case "RECEIVED" -> BookingStatus.RECEIVED_BY_LAUNDRY;
            case "COMPLETED" -> BookingStatus.COMPLETED;
            default -> {
                try {
                    yield BookingStatus.valueOf(statusStr);
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "Invalid booking status: " + rawStatus + ". Allowed values: " +
                                    Arrays.toString(BookingStatus.values()) +
                                    " (aliases: ASSIGNED, RECEIVED, COMPLETED)"
                    );
                }
            }
        };
    }

    @PostMapping("/{bookingId}/delivery/accept")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<MessageResponse> acceptDeliveryOffer(@PathVariable String bookingId) {
        boolean accepted = bookingService.acceptDeliveryOffer(bookingId);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Delivery offer could not be accepted"));
        }
        return ResponseEntity.ok(new MessageResponse("Delivery offer accepted"));
    }

    @PostMapping("/{bookingId}/delivery/decline")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<MessageResponse> declineDeliveryOffer(@PathVariable String bookingId) {
        boolean declined = bookingService.declineDeliveryOffer(bookingId);
        if (!declined) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Delivery offer could not be declined"));
        }
        return ResponseEntity.ok(new MessageResponse("Delivery offer declined"));
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
        return ResponseEntity.ok(new MessageResponse("Laundry completed and delivery agents notified"));
    }

    @PostMapping("/{bookingId}/delivery/early-response")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<MessageResponse> respondToEarlyDelivery(
            @PathVariable String bookingId,
            @Valid @RequestBody EarlyDeliveryDecisionRequest request) {
        bookingService.respondToEarlyDeliveryOption(bookingId, request.getAcceptTomorrow());
        return ResponseEntity.ok(new MessageResponse("Early delivery preference recorded"));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<MessageResponse> cancelBooking(@PathVariable String bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(new MessageResponse("Booking cancelled successfully"));
    }
}

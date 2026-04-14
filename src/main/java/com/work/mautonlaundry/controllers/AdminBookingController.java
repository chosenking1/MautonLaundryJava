package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.services.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<Page<BookingDetailsResponse>> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(page, size);
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(bookingService.getAllBookings(pageable));
        }

        try {
            BookingStatus bookingStatus = BookingStatus.valueOf(status.trim().toUpperCase());
            return ResponseEntity.ok(bookingService.getAllBookingsByStatus(pageable, bookingStatus));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }
    }

    @PostMapping("/{bookingId}/laundry-agent/{laundryAgentId}")
    public ResponseEntity<MessageResponse> assignLaundryAgentByAdmin(
            @PathVariable String bookingId,
            @PathVariable String laundryAgentId) {
        bookingService.assignLaundryAgentByAdmin(bookingId, laundryAgentId);
        return ResponseEntity.ok(new MessageResponse("Laundry agent assigned by admin"));
    }

    @PostMapping("/{bookingId}/force-reassign/{laundryAgentId}")
    public ResponseEntity<MessageResponse> forceReassignLaundryAgent(
            @PathVariable String bookingId,
            @PathVariable String laundryAgentId) {
        bookingService.assignLaundryAgentByAdmin(bookingId, laundryAgentId);
        return ResponseEntity.ok(new MessageResponse("Laundry agent force-reassigned and TTL restarted"));
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<CreateBookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse response = bookingService.createBooking(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ResponseEntity<BookingDetailsResponse> getBooking(@PathVariable String bookingId) {
        BookingDetailsResponse response = bookingService.getBookingDetails(bookingId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingId}/status")
    @PreAuthorize("hasAuthority('BOOKING_UPDATE')")
    public ResponseEntity<Map<String, String>> updateBookingStatus(
            @PathVariable String bookingId,
            @RequestBody Map<String, String> request) {
        
        String statusStr = request.get("status");
        Booking.BookingStatus status = Booking.BookingStatus.valueOf(statusStr);
        
        bookingService.updateBookingStatus(bookingId, status);
        
        return ResponseEntity.ok(Map.of("message", "Status updated successfully"));
    }
}
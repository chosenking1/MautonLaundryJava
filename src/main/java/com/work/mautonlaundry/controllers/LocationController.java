package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.locationrequests.UpdateCurrentLocationRequest;
import com.work.mautonlaundry.dtos.requests.locationrequests.UpdateLocationRequest;
import com.work.mautonlaundry.dtos.responses.locationresponses.DeliveryLocationResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.LocationTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationTrackingService locationTrackingService;

    @PostMapping("/update")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<String> updateLocation(@Valid @RequestBody UpdateLocationRequest request) {
        locationTrackingService.updateDeliveryLocation(request);
        return ResponseEntity.ok("Location updated successfully");
    }

    @PostMapping("/current")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<String> updateCurrentLocation(@Valid @RequestBody UpdateCurrentLocationRequest request) {
        locationTrackingService.updateCurrentLocation(request);
        return ResponseEntity.ok("Current location updated successfully");
    }

    @GetMapping("/delivery/{bookingId}")
    public ResponseEntity<DeliveryLocationResponse> getDeliveryLocation(@PathVariable String bookingId) {
        String currentUserEmail = SecurityUtil.getCurrentUser()
                .map(user -> user.getEmail())
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        DeliveryLocationResponse location = locationTrackingService.getDeliveryLocation(bookingId, currentUserEmail);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(location);
    }
}

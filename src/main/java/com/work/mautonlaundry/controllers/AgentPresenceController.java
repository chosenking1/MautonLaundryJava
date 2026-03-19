package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.dtos.requests.agentrequests.AgentAvailabilityLocationRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AgentPresenceController {

    private final TrackingService trackingService;

    @PostMapping({"/api/v1/agents/online", "/agents/online"})
    @PreAuthorize("hasRole('DELIVERY_AGENT') or hasRole('LAUNDRY_AGENT')")
    public ResponseEntity<MessageResponse> markOnline() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        trackingService.markAgentOnline(currentUser.getId());
        return ResponseEntity.ok(new MessageResponse("Agent marked online"));
    }

    @PostMapping({"/api/v1/agents/offline", "/agents/offline"})
    @PreAuthorize("hasRole('DELIVERY_AGENT') or hasRole('LAUNDRY_AGENT')")
    public ResponseEntity<MessageResponse> markOffline() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        trackingService.markAgentOffline(currentUser.getId());
        return ResponseEntity.ok(new MessageResponse("Agent marked offline"));
    }

    @PostMapping({"/api/v1/agents/location", "/agents/location"})
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<MessageResponse> updateAvailabilityLocation(
            @Valid @RequestBody AgentAvailabilityLocationRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        trackingService.handleAvailabilityLocationUpdate(
                currentUser.getId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getTimestamp()
        );
        return ResponseEntity.ok(new MessageResponse("Location updated"));
    }
}

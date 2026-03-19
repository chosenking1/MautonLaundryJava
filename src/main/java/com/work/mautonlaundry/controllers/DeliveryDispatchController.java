package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.deliveryrequests.AcceptDeliveryJobRequest;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.ForceDispatchJobRequest;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.UpdateDeliveryStatusRequest;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AcceptDeliveryJobResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AvailableDeliveryJobResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryAssignmentSummaryResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryStatusUpdateResponse;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.DeliveryService;
import com.work.mautonlaundry.services.DispatchEngine;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/deliveries", "/deliveries"})
@RequiredArgsConstructor
public class DeliveryDispatchController {
    private final DeliveryService deliveryService;
    private final DispatchEngine dispatchEngine;

    @GetMapping("/available")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<List<AvailableDeliveryJobResponse>> getAvailableJobs() {
        String agentId = SecurityUtil.getCurrentUser().orElseThrow().getId();
        return ResponseEntity.ok(dispatchEngine.getAvailableJobsForAgent(agentId));
    }

    @GetMapping("/assignments/active")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<List<DeliveryAssignmentSummaryResponse>> getActiveAssignments() {
        String agentId = SecurityUtil.getCurrentUser().orElseThrow().getId();
        return ResponseEntity.ok(deliveryService.getActiveAssignmentsForAgent(agentId));
    }

    @PostMapping("/accept")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<AcceptDeliveryJobResponse> acceptDeliveryJob(
            @Valid @RequestBody AcceptDeliveryJobRequest request) {
        return ResponseEntity.ok(deliveryService.acceptDeliveryJob(request));
    }

    @PostMapping("/{bookingId}/status")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<DeliveryStatusUpdateResponse> updateDeliveryStatus(
            @PathVariable String bookingId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return ResponseEntity.ok(deliveryService.updateDeliveryStatus(bookingId, request));
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> forceDispatchJob(
            @Valid @RequestBody ForceDispatchJobRequest request) {
        DeliveryAssignmentPhase phase = null;
        if (request.getPhase() != null && !request.getPhase().isBlank()) {
            try {
                phase = DeliveryAssignmentPhase.valueOf(request.getPhase().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid phase: " + request.getPhase());
            }
        }
        dispatchEngine.forceDispatchJob(request.getBookingId(), phase);
        return ResponseEntity.ok(new MessageResponse("Dispatch job enqueued"));
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AgentApplication;
import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import com.work.mautonlaundry.dtos.requests.agentapplications.CreateDeliveryApplicationRequest;
import com.work.mautonlaundry.dtos.requests.agentapplications.CreateLaundryApplicationRequest;
import com.work.mautonlaundry.dtos.requests.agentapplications.InspectAgentApplicationRequest;
import com.work.mautonlaundry.dtos.requests.agentapplications.RejectAgentApplicationRequest;
import com.work.mautonlaundry.dtos.responses.agentapplications.AgentApplicationResponse;
import com.work.mautonlaundry.services.AgentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AgentApplicationController {

    private final AgentApplicationService agentApplicationService;

    @PostMapping("/api/v1/agent-applications/laundry")
    public ResponseEntity<AgentApplicationResponse> submitLaundryApplication(
            @Valid @RequestBody CreateLaundryApplicationRequest request) {
        AgentApplication application = agentApplicationService.submitLaundryApplication(request.getAddressIds());
        return ResponseEntity.ok(toResponse(application));
    }

    @PostMapping("/api/v1/agent-applications/delivery")
    public ResponseEntity<AgentApplicationResponse> submitDeliveryApplication(
            @Valid @RequestBody CreateDeliveryApplicationRequest request) {
        AgentApplication application = agentApplicationService.submitDeliveryApplication(request.getAddressId());
        return ResponseEntity.ok(toResponse(application));
    }

    @GetMapping("/api/v1/admin/agent-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgentApplicationResponse>> getApplications(
            @RequestParam(required = false) AgentApplicationStatus status,
            @RequestParam(required = false) AgentApplicationType type) {
        List<AgentApplicationResponse> responses = agentApplicationService.getApplications(status, type).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/api/v1/admin/agent-applications/{id}/inspection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentApplicationResponse> inspectLaundryApplication(
            @PathVariable Long id,
            @Valid @RequestBody InspectAgentApplicationRequest request) {
        AgentApplication application = agentApplicationService.inspectLaundryApplication(
                id,
                Boolean.TRUE.equals(request.getPassed()),
                request.getFailedAddressIds(),
                request.getNotes(),
                request.getRejectionReason()
        );
        return ResponseEntity.ok(toResponse(application));
    }

    @PatchMapping("/api/v1/admin/agent-applications/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentApplicationResponse> rejectApplication(
            @PathVariable Long id,
            @Valid @RequestBody RejectAgentApplicationRequest request) {
        AgentApplication application = agentApplicationService.rejectApplication(id, request.getReason());
        return ResponseEntity.ok(toResponse(application));
    }

    @PostMapping("/api/v1/admin/agent-applications/{id}/role-change")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> createRoleChangeRequest(@PathVariable Long id) {
        agentApplicationService.createRoleChangeRequest(id);
        return ResponseEntity.ok().build();
    }

    private AgentApplicationResponse toResponse(AgentApplication application) {
        AgentApplicationResponse response = new AgentApplicationResponse();
        response.setId(application.getId());
        response.setUserId(application.getUser().getId());
        response.setUserEmail(application.getUser().getEmail());
        response.setType(application.getType());
        response.setStatus(application.getStatus());
        response.setInspectionStatus(application.getInspectionStatus());
        response.setRejectionReason(application.getRejectionReason());
        response.setInspectionNotes(application.getInspectionNotes());
        response.setAddressIds(application.getAddresses().stream().map(a -> a.getId()).toList());
        response.setCreatedAt(application.getCreatedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }
}

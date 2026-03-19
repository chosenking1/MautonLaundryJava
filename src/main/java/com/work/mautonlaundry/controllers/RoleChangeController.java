package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.dtos.requests.rolechangerequests.CreateRoleChangeRequest;
import com.work.mautonlaundry.dtos.requests.rolechangerequests.UpdateRoleChangeStatusRequest;
import com.work.mautonlaundry.services.RoleChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/role-requests")
@RequiredArgsConstructor
public class RoleChangeController {
    
    private final RoleChangeService roleChangeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_CHANGE_CREATE')")
    public ResponseEntity<RoleChangeRequest> createRequest(@Valid @RequestBody CreateRoleChangeRequest request) {
        String userId = request.getUserId();
        String roleName = request.getRequestedRole();
        
        RoleChangeRequest created = roleChangeService.createRequest(userId, roleName);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{requestId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_CHANGE_UPDATE')")
    public ResponseEntity<RoleChangeRequest> updateRequestStatus(
            @PathVariable Long requestId,
            @Valid @RequestBody UpdateRoleChangeStatusRequest request) {
        
        String status = request.getStatus();
        
        if ("APPROVED".equals(status)) {
            return ResponseEntity.ok(roleChangeService.approveRequest(requestId));
        } else if ("REJECTED".equals(status)) {
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new IllegalArgumentException("Rejection reason is required");
            }
            return ResponseEntity.ok(roleChangeService.rejectRequest(requestId, request.getReason()));
        } else {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_CHANGE_READ')")
    public ResponseEntity<List<RoleChangeRequest>> getPendingRequests() {
        return ResponseEntity.ok(roleChangeService.getPendingRequests());
    }
}

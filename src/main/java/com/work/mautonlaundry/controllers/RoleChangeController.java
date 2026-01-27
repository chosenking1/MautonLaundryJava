package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.services.RoleChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/role-requests")
@PreAuthorize("hasAuthority('ROLE_CHANGE_READ')") // Assuming read access to the endpoint implies general access
@RequiredArgsConstructor
public class RoleChangeController {
    
    private final RoleChangeService roleChangeService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CHANGE_CREATE')")
    public ResponseEntity<RoleChangeRequest> createRequest(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String roleName = request.get("requestedRole");
        
        RoleChangeRequest created = roleChangeService.createRequest(userId, roleName);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{requestId}")
    @PreAuthorize("hasAuthority('ROLE_CHANGE_UPDATE')")
    public ResponseEntity<RoleChangeRequest> updateRequestStatus(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        
        if ("APPROVED".equals(status)) {
            return ResponseEntity.ok(roleChangeService.approveRequest(requestId));
        } else if ("REJECTED".equals(status)) {
            return ResponseEntity.ok(roleChangeService.rejectRequest(requestId));
        } else {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ROLE_CHANGE_READ')")
    public ResponseEntity<List<RoleChangeRequest>> getPendingRequests() {
        return ResponseEntity.ok(roleChangeService.getPendingRequests());
    }
}

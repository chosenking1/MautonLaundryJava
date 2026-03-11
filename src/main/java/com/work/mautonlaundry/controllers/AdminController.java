package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.AnalyticsService;
import com.work.mautonlaundry.services.UserService;
import com.work.mautonlaundry.dtos.responses.analytics.AuditLogResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final AnalyticsService analyticsService;
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<FindUserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FindUserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogResponse> auditLogs = analyticsService.getAuditLogs(pageable);
        return ResponseEntity.ok(auditLogs);
    }

}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.UserLoginResponse;
import com.work.mautonlaundry.security.service.AuthService;
import com.work.mautonlaundry.services.AuditService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyAuthController {

    private static final Logger log = LoggerFactory.getLogger(LegacyAuthController.class);

    private final AuthService authService;
    private final AuditService auditService;

    public LegacyAuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            String token = authService.login(request);
            auditService.logAction("LOGIN", "AUTH", request.getEmail());
            return ResponseEntity.ok(new UserLoginResponse(token, "Bearer"));
        } catch (Exception e) {
            auditService.logAction("LOGIN_FAILED", "AUTH", request.getEmail());
            log.warn("Legacy login failed for email: {} - {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}

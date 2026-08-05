package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * The terms currently in force, and re-acceptance.
 *
 * <p>Reading the current version is public: an app has to know what to show on
 * the registration screen, before anyone has an account.
 */
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @GetMapping("/current")
    public ResponseEntity<Map<String, String>> current() {
        Map<String, String> body = new HashMap<>();
        body.put("version", termsService.currentVersion());
        body.put("url", termsService.termsUrl());
        return ResponseEntity.ok(body);
    }

    /** Re-acceptance by an existing user after the terms change. */
    @PostMapping("/accept")
    public ResponseEntity<Map<String, String>> accept(
            @RequestBody(required = false) AcceptRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
        AppUser user = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Not signed in"));
        termsService.record(
                user,
                request == null ? null : request.version(),
                request == null ? null : request.source(),
                forwardedFor);
        return ResponseEntity.ok(Map.of("version", termsService.currentVersion()));
    }

    public record AcceptRequest(String version, String source) {
    }
}

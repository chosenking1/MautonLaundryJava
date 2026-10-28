package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AccountDeletionRequest;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.dtos.requests.AccountDeletionRequestBody;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.AccountDeletionService;
import com.work.mautonlaundry.services.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Account deletion, from both the places Google Play requires: inside the app,
 * and a public web page usable without the app installed.
 */
@RestController
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;
    private final AuditService auditService;

    /** In-app, signed in. Any authenticated customer, for their own account only. */
    @PostMapping("/api/v1/account/deletion")
    public ResponseEntity<Map<String, Object>> requestFromApp(@Valid @RequestBody AccountDeletionRequestBody body) {
        AppUser user = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        AccountDeletionRequest request = accountDeletionService.requestForUser(user, body.getPassword());
        auditService.logAction("ACCOUNT_DELETION_REQUESTED", "USER", user.getId());
        return ResponseEntity.ok(response(request));
    }

    /** The public page at imototo.com.ng/delete-account/. Under /api/auth, so permitted and rate-limited. */
    @PostMapping("/api/auth/delete-account")
    public ResponseEntity<Map<String, Object>> requestFromWeb(@Valid @RequestBody AccountDeletionRequestBody body) {
        AccountDeletionRequest request =
                accountDeletionService.requestWithCredentials(body.getEmail(), body.getPassword());
        return ResponseEntity.ok(response(request));
    }

    private static Map<String, Object> response(AccountDeletionRequest request) {
        LocalDate date = request.getScheduledFor().toLocalDate();
        return Map.of(
                "message", "Your account will be deleted on " + date + ". Log in before then to cancel.",
                "scheduledFor", date.toString());
    }
}

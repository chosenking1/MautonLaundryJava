package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.services.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @PatchMapping("/{userId}/deactivate-agent")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('AGENT_DEACTIVATE')")
    public ResponseEntity<MessageResponse> deactivateAgent(
            @PathVariable String userId,
            @RequestBody(required = false) DeactivateAgentRequest request) {
        String reason = request == null ? null : request.getReason();
        userService.deactivateAgent(userId, reason);
        return ResponseEntity.ok(new MessageResponse("Agent deactivated successfully"));
    }

    /**
     * Resends the verification email for any customer, whether or not earlier
     * sends reached them. Backed by the same replace-the-token flow the public
     * resend uses, so only the newest link stays valid.
     */
    @PostMapping("/{userId}/resend-verification")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('USER_UPDATE')")
    public ResponseEntity<MessageResponse> resendVerification(@PathVariable String userId) {
        userService.resendVerificationById(userId);
        return ResponseEntity.ok(new MessageResponse("Verification email sent"));
    }

    @Data
    private static class DeactivateAgentRequest {
        private String reason;
    }
}

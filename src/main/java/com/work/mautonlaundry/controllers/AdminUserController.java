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
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @PatchMapping("/{userId}/deactivate-agent")
    public ResponseEntity<MessageResponse> deactivateAgent(
            @PathVariable String userId,
            @RequestBody(required = false) DeactivateAgentRequest request) {
        String reason = request == null ? null : request.getReason();
        userService.deactivateAgent(userId, reason);
        return ResponseEntity.ok(new MessageResponse("Agent deactivated successfully"));
    }

    @Data
    private static class DeactivateAgentRequest {
        private String reason;
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.PendingPermissionReview;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.PendingPermissionReviewRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The API behind the User Management screen (Permission Architecture V2, §7.4):
 * a user's effective permissions, granting and denying them, setting their data
 * scope, and the pending-permission-review queue.
 *
 * <p>Guards mirror the two capabilities V20 seeds. PERMISSION_ASSIGN gates the
 * grant/deny/revoke surface; SCOPE_ASSIGN gates scope, because deciding what
 * someone may do and whose data they see are different powers (spec §5.1).
 * ACCESS_VIEW gates read.
 *
 * <p>A grant the caller cannot make directly does not fail here -- UserAccessService
 * opens a maker-checker request and this returns 202 with its id, so the client
 * can tell "done" from "awaiting approval".
 */
@RestController
@RequestMapping("/api/v1/admin/access")
@RequiredArgsConstructor
public class AccessControlController {

    private final UserAccessService userAccessService;
    private final PermissionRepository permissionRepository;
    private final PendingPermissionReviewRepository pendingReviewRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    /** A user's effective permissions -- what the User Management screen shows. */
    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('ACCESS_VIEW')")
    public ResponseEntity<Map<String, Object>> effectivePermissions(@PathVariable String userId) {
        Set<String> effective = permissionEvaluationService.getEffectivePermissions(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "permissions", effective.stream().sorted().toList()));
    }

    @PostMapping("/users/{userId}/permissions/{permissionId}/grant")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PERMISSION_ASSIGN')")
    public ResponseEntity<Map<String, Object>> grant(@PathVariable String userId, @PathVariable Long permissionId) {
        UserAccessService.GrantOutcome outcome =
                userAccessService.grant(userId, permissionId, SecurityUtil.getCurrentUserId());
        if (outcome.pendingApproval()) {
            // Maker-checker: not done, awaiting a second pair of eyes (spec §2.2).
            return ResponseEntity.accepted()
                    .body(Map.of("status", "PENDING_APPROVAL", "requestId", outcome.requestId()));
        }
        return ResponseEntity.ok(Map.of("status", "GRANTED"));
    }

    @PostMapping("/users/{userId}/permissions/{permissionId}/deny")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PERMISSION_ASSIGN')")
    public ResponseEntity<Void> deny(@PathVariable String userId, @PathVariable Long permissionId) {
        userAccessService.deny(userId, permissionId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/permissions/{permissionId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PERMISSION_ASSIGN')")
    public ResponseEntity<Void> revoke(@PathVariable String userId, @PathVariable Long permissionId) {
        userAccessService.revoke(userId, permissionId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/scope")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_ASSIGN')")
    public ResponseEntity<Void> assignScope(@PathVariable String userId, @RequestBody ScopeRequest request) {
        userAccessService.assignScope(userId, request.level, request.regionId, request.stateId,
                request.zoneId, request.specialistUserId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * The review queue (spec §2.1/§7.4): grants whose grantor was reduced below
     * them, awaiting a keep-or-revoke decision.
     */
    @GetMapping("/reviews")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('ACCESS_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> reviewQueue() {
        return ResponseEntity.ok(pendingReviewRepository.findUnreviewed().stream()
                .map(this::reviewView).toList());
    }

    @PostMapping("/reviews/{reviewId}/resolve")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PERMISSION_ASSIGN')")
    public ResponseEntity<Void> resolveReview(@PathVariable String reviewId, @RequestParam boolean keep) {
        userAccessService.resolveReview(reviewId, keep, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    private Map<String, Object> reviewView(PendingPermissionReview r) {
        String permissionName = permissionRepository.findById(r.getPermissionId())
                .map(Permission::getName).orElse("#" + r.getPermissionId());
        return Map.of(
                "id", r.getId(),
                "userId", r.getUserId(),
                "permission", permissionName,
                "originalGrantorId", r.getOriginalGrantorId(),
                "flaggedAt", String.valueOf(r.getFlaggedAt()));
    }

    public static class ScopeRequest {
        public ScopeLevel level;
        public String regionId;
        public Integer stateId;
        public String zoneId;
        public String specialistUserId;
    }
}

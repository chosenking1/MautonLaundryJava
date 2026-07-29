package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.ScopeUpgradeRequest;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.model.enums.ScopeUpgradeStatus;
import com.work.mautonlaundry.data.repository.ScopeUpgradeRequestRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.ScopeUpgradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The API behind Temporary Scope Request Management (spec §7.6).
 *
 * <p>Anyone with SCOPE_UPGRADE_REQUEST may ask (§6.1: "any user can request").
 * Reviewing is gated by SCOPE_UPGRADE_REVIEW, but that only gates entry --
 * whether the caller can approve a specific request depends on their own scope
 * covering the requested area, which ScopeUpgradeService checks. The permission
 * cannot itself grant that coverage.
 */
@RestController
@RequestMapping("/api/v1/admin/scope-requests")
@RequiredArgsConstructor
public class ScopeUpgradeController {

    private final ScopeUpgradeService scopeUpgradeService;
    private final ScopeUpgradeRequestRepository requestRepository;

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_UPGRADE_REQUEST')")
    public ResponseEntity<Map<String, Object>> request(@RequestBody UpgradeRequest body) {
        ScopeUpgradeRequest created = scopeUpgradeService.requestUpgrade(
                SecurityUtil.getCurrentUserId(), body.level, body.regionId, body.stateId, body.zoneId, body.reason);
        return ResponseEntity.ok(Map.of("id", created.getId(), "status", String.valueOf(created.getStatus())));
    }

    /** My own requests and where they stand (spec §7.6 history for the requester). */
    @GetMapping("/mine")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_UPGRADE_REQUEST')")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(
                requestRepository.findByRequesterIdOrderByRequestedAtDesc(SecurityUtil.getCurrentUserId())
                        .stream().map(ScopeUpgradeController::view).toList());
    }

    /** The review queue: pending requests, for someone who may approve them. */
    @GetMapping("/pending")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_UPGRADE_REVIEW')")
    public ResponseEntity<List<Map<String, Object>>> pending() {
        return ResponseEntity.ok(
                requestRepository.findByStatusOrderByRequestedAtAsc(ScopeUpgradeStatus.PENDING)
                        .stream().map(ScopeUpgradeController::view).toList());
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_UPGRADE_REVIEW')")
    public ResponseEntity<Void> approve(@PathVariable String requestId, @RequestBody ApproveRequest body) {
        // Coverage of the requested area is enforced inside the service.
        scopeUpgradeService.approveUpgrade(requestId, SecurityUtil.getCurrentUserId(), body.expiry);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SCOPE_UPGRADE_REVIEW')")
    public ResponseEntity<Void> reject(@PathVariable String requestId, @RequestBody RejectRequest body) {
        scopeUpgradeService.rejectUpgrade(requestId, SecurityUtil.getCurrentUserId(),
                body == null ? null : body.reason);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    private static Map<String, Object> view(ScopeUpgradeRequest r) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", r.getId());
        m.put("requesterId", r.getRequesterId());
        m.put("currentScope", r.getCurrentScopeLevel());
        m.put("requestedLevel", String.valueOf(r.getRequestedScopeLevel()));
        m.put("requestedStateId", r.getRequestedStateId());
        m.put("requestedZoneId", r.getRequestedZoneId());
        m.put("reason", r.getReason());
        m.put("status", String.valueOf(r.getStatus()));
        m.put("expiry", String.valueOf(r.getExpiry()));
        m.put("requestedAt", String.valueOf(r.getRequestedAt()));
        return m;
    }

    public static class UpgradeRequest {
        public ScopeLevel level;
        public String regionId;
        public Integer stateId;
        public String zoneId;
        public String reason;
    }

    public static class ApproveRequest {
        /** ISO-8601; when the upgrade lapses. Must be in the future (spec §6.1). */
        public LocalDateTime expiry;
    }

    public static class RejectRequest {
        public String reason;
    }
}

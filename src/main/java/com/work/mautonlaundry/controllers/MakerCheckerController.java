package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.MakerCheckerRequest;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.enums.MakerCheckerStatus;
import com.work.mautonlaundry.data.repository.MakerCheckerRequestRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.MakerCheckerService;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The API behind the Maker-Checker Approval Queue (spec §7.5).
 *
 * <p>Reading the queue is gated by MAKER_CHECKER_VIEW. Deciding is NOT gated by a
 * blanket permission: eligibility to approve a given request is whether the
 * caller personally holds the permission it would assign (spec §2.3), which
 * MakerCheckerService checks. A permission-gate on the endpoint would be the
 * wrong shape -- it is per-request, not per-user.
 *
 * <p>The queue marks which requests the caller is actually eligible to decide
 * (spec §7.5), so the client can highlight them rather than offering an approve
 * button that will 400.
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
public class MakerCheckerController {

    private final MakerCheckerService makerCheckerService;
    private final MakerCheckerRequestRepository requestRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MAKER_CHECKER_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> pending() {
        String me = SecurityUtil.getCurrentUserId();
        Set<String> myPermissions = permissionEvaluationService.getEffectivePermissions(me);

        return ResponseEntity.ok(
                requestRepository.findByStatusOrderByCreatedAtAsc(MakerCheckerStatus.PENDING).stream()
                        .map(r -> view(r, me, myPermissions)).toList());
    }

    /** The caller's own submitted requests and their status (spec §7.5 "My requests" tab). */
    @GetMapping("/mine")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MAKER_CHECKER_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        String me = SecurityUtil.getCurrentUserId();
        Set<String> myPermissions = permissionEvaluationService.getEffectivePermissions(me);
        return ResponseEntity.ok(requestRepository.findByMakerIdOrderByCreatedAtDesc(me).stream()
                .map(r -> view(r, me, myPermissions)).toList());
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MAKER_CHECKER_VIEW')")
    public ResponseEntity<Void> approve(@PathVariable String requestId) {
        // Eligibility is enforced inside the service, per request.
        makerCheckerService.approve(requestId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('MAKER_CHECKER_VIEW')")
    public ResponseEntity<Void> reject(@PathVariable String requestId, @RequestBody RejectRequest body) {
        makerCheckerService.reject(requestId, SecurityUtil.getCurrentUserId(),
                body == null ? null : body.reason);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    private Map<String, Object> view(MakerCheckerRequest r, String me, Set<String> myPermissions) {
        String required = r.getRequiredCheckerPermissionId() == null ? null
                : permissionRepository.findById(r.getRequiredCheckerPermissionId())
                        .map(Permission::getName).orElse(null);
        // Eligible = I hold the required permission and I am not the maker
        // (a second pair of eyes cannot be the first). Or the fallback path.
        boolean eligible = !me.equals(r.getMakerId())
                && (required != null && myPermissions.contains(required)
                    || Boolean.TRUE.equals(r.getFallbackToSuperAdmin())
                       && myPermissions.contains("MAKER_CHECKER_FALLBACK"));

        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", r.getId());
        m.put("requestType", String.valueOf(r.getRequestType()));
        m.put("makerId", r.getMakerId());
        m.put("targetUserId", r.getTargetUserId());
        m.put("requiredPermission", required);
        m.put("escalated", r.getEscalatedAt() != null);
        m.put("fallback", r.getFallbackToSuperAdmin());
        m.put("createdAt", String.valueOf(r.getCreatedAt()));
        m.put("iAmEligible", eligible);
        return m;
    }

    public static class RejectRequest {
        public String reason;
    }
}

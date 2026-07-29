package com.work.mautonlaundry.security;

import com.work.mautonlaundry.data.model.MakerCheckerRequest;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.enums.MakerCheckerStatus;
import com.work.mautonlaundry.data.model.enums.MakerCheckerType;
import com.work.mautonlaundry.data.repository.MakerCheckerRequestRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.SystemConfigRepository;
import com.work.mautonlaundry.services.AuditService;
import com.work.mautonlaundry.services.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Approvals for assignments beyond the maker's own permission set
 * (Permission Architecture V2, spec §2.3, §8.4).
 *
 * <h2>What it is for</h2>
 * Spec §2.2 allows a Permission Admin to delegate <em>any</em> permission in the
 * system without personally holding it -- a CEO can hand routine access
 * management to an HR admin who does not themselves hold PAYOUT_SETTLE. The
 * safeguard is not blocking that; it is ensuring the person who signs it off can
 * evaluate it.
 *
 * <h2>Eligibility is the whole security property</h2>
 * A checker must <b>personally hold</b> the permission being assigned (§2.3).
 * Without that, two equally uninformed admins could rubber-stamp each other into
 * anything. The check is per-person and never aggregate: two ineligible people do
 * not add up to one eligible one.
 *
 * <p>"Personally holds" is resolved by the same rules enforcement uses -- role,
 * module, direct grant, minus exclusions and DENY. It has to agree, or someone
 * gets asked to approve something they could not do themselves.
 *
 * <h2>Fallback: a permission, not a role</h2>
 * §2.3 nominates "SUPER_ADMIN or CEO" when nobody holds the permission yet
 * (bootstrapping). Neither role exists here, and §11 bans hardcoded role names
 * anyway, so the fallback is MAKER_CHECKER_FALLBACK -- granted to ADMIN, and
 * grantable to a CEO account if one is ever created.
 *
 * <p>When no eligible checker exists at submission, the request is flagged for
 * fallback <em>immediately</em> rather than waiting out the escalation window
 * (§8.4) -- there is no point waiting for a decision nobody can make.
 */
@Service
@RequiredArgsConstructor
public class MakerCheckerService {

    private static final Logger log = LoggerFactory.getLogger(MakerCheckerService.class);

    static final String ESCALATION_HOURS_KEY = "maker_checker_escalation_hours";
    static final String EXPIRY_DAYS_KEY = "maker_checker_expiry_days";
    static final long DEFAULT_ESCALATION_HOURS = 24;
    static final long DEFAULT_EXPIRY_DAYS = 7;

    private final MakerCheckerRequestRepository requestRepository;
    private final PermissionRepository permissionRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final AuditService auditService;

    /** Lazy: UserAccessService defers to this service, which executes through it on approval. */
    @Lazy
    private final UserAccessService userAccessService;

    /**
     * Opens a request to grant a permission the maker does not hold.
     *
     * @return the pending request; callers must not treat this as executed
     */
    @Transactional
    public MakerCheckerRequest submitPermissionAssign(String makerId, String targetUserId, Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        List<String> eligible = getEligibleCheckers(permissionId);

        MakerCheckerRequest request = new MakerCheckerRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestType(MakerCheckerType.PERMISSION_ASSIGN);
        request.setMakerId(makerId);
        request.setTargetUserId(targetUserId);
        request.setTargetPermissionId(permissionId);
        request.setRequiredCheckerPermissionId(permissionId);
        request.setFallbackToSuperAdmin(eligible.isEmpty());
        request.setStatus(MakerCheckerStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        request.setExpiresAt(LocalDateTime.now().plusDays(expiryDays()));
        requestRepository.save(request);

        auditService.logChange("MAKER_CHECKER_SUBMIT", "MAKER_CHECKER_REQUEST", request.getId(),
                null, describe(request, permission.getName()));

        if (eligible.isEmpty()) {
            // Nobody can evaluate this. Waiting out the escalation window would
            // achieve nothing, so the fallback approvers are the audience now.
            log.info("Request {} has no eligible checker for {}; flagged for fallback approval",
                    request.getId(), permission.getName());
        } else {
            log.info("Request {} submitted by {} for {}; {} eligible checker(s)",
                    request.getId(), makerId, permission.getName(), eligible.size());
        }
        return request;
    }

    /**
     * Everyone who personally holds the permission, and may therefore approve.
     * Used both to notify and to validate at decision time (spec §8.4).
     */
    public List<String> getEligibleCheckers(Long permissionId) {
        return requestRepository.findEligibleCheckerIds(permissionId);
    }

    /**
     * Approves and executes the assignment atomically (spec §8.4).
     *
     * @throws IllegalArgumentException if the checker is not eligible, or if the
     *         request is no longer pending
     */
    @Transactional
    public void approve(String requestId, String checkerId) {
        MakerCheckerRequest request = requirePending(requestId);
        requireEligible(request, checkerId);

        if (request.getMakerId().equals(checkerId)) {
            // Not in the spec, but implied by the entire point of the mechanism:
            // a second pair of eyes cannot be the first pair.
            throw new IllegalArgumentException("You cannot approve your own request.");
        }

        request.setStatus(MakerCheckerStatus.APPROVED);
        request.setCheckerId(checkerId);
        request.setCheckedAt(LocalDateTime.now());
        requestRepository.save(request);

        execute(request);

        // Spec §8.4: the audit records both identities. A single actor field
        // would lose the fact that two people were involved, which is the only
        // thing the mechanism produces.
        Map<String, Object> after = new HashMap<>();
        after.put("status", "APPROVED");
        after.put("makerId", request.getMakerId());
        after.put("checkerId", checkerId);
        auditService.logChange("MAKER_CHECKER_APPROVE", "MAKER_CHECKER_REQUEST", requestId,
                Map.of("status", "PENDING"), after);

        log.info("Request {} approved by {} (maker {})", requestId, checkerId, request.getMakerId());
    }

    @Transactional
    public void reject(String requestId, String checkerId, String reason) {
        MakerCheckerRequest request = requirePending(requestId);
        requireEligible(request, checkerId);

        request.setStatus(MakerCheckerStatus.REJECTED);
        request.setCheckerId(checkerId);
        request.setCheckedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        requestRepository.save(request);

        Map<String, Object> after = new HashMap<>();
        after.put("status", "REJECTED");
        after.put("checkerId", checkerId);
        after.put("reason", reason);
        auditService.logChange("MAKER_CHECKER_REJECT", "MAKER_CHECKER_REQUEST", requestId,
                Map.of("status", "PENDING"), after);

        log.info("Request {} rejected by {}: {}", requestId, checkerId, reason);
    }

    /**
     * Chases requests nobody has decided (spec §8.4).
     *
     * @return how many were escalated
     */
    @Transactional
    public int processEscalations() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(escalationHours());
        List<MakerCheckerRequest> stale = requestRepository.findNeedingEscalation(threshold);
        for (MakerCheckerRequest r : stale) {
            r.setEscalatedAt(LocalDateTime.now());
            requestRepository.save(r);
            log.info("Request {} escalated after {}h without a decision", r.getId(), escalationHours());
        }
        return stale.size();
    }

    /**
     * Abandons requests nobody decided in time (spec §8.4).
     *
     * <p>Expiry is a safety property, not tidying: a request that sits forever is
     * an assignment somebody still expects to happen.
     *
     * @return how many expired
     */
    @Transactional
    public int expireStale() {
        List<MakerCheckerRequest> expired = requestRepository.findExpired(LocalDateTime.now());
        for (MakerCheckerRequest r : expired) {
            r.setStatus(MakerCheckerStatus.EXPIRED);
            requestRepository.save(r);
            auditService.logChange("MAKER_CHECKER_EXPIRE", "MAKER_CHECKER_REQUEST", r.getId(),
                    Map.of("status", "PENDING"), Map.of("status", "EXPIRED"));
            log.info("Request {} expired unactioned; maker {} must resubmit", r.getId(), r.getMakerId());
        }
        return expired.size();
    }

    // ---- internals ----

    /** Executes on behalf of the CHECKER, who is the one accountable for it. */
    private void execute(MakerCheckerRequest request) {
        if (request.getRequestType() == MakerCheckerType.PERMISSION_ASSIGN) {
            userAccessService.grantApproved(request.getTargetUserId(),
                    request.getTargetPermissionId(), request.getCheckerId());
            return;
        }
        // MODULE_ASSIGN / ROLE_ASSIGN submission paths do not exist yet, so a
        // request of that type cannot have been created. Fail loudly rather than
        // silently approve nothing.
        throw new UnsupportedOperationException(
                "No execution path for request type " + request.getRequestType());
    }

    private MakerCheckerRequest requirePending(String requestId) {
        MakerCheckerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        if (request.getStatus() != MakerCheckerStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is already " + request.getStatus() + " and cannot be decided again.");
        }
        return request;
    }

    /**
     * Spec §2.3: the checker must personally hold the permission -- or hold the
     * fallback permission, when nobody held it at submission time.
     */
    private void requireEligible(MakerCheckerRequest request, String checkerId) {
        Permission required = permissionRepository.findById(request.getRequiredCheckerPermissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Request " + request.getId() + " names a permission that no longer exists"));

        if (permissionEvaluationService.getEffectivePermissions(checkerId).contains(required.getName())) {
            return;
        }
        if (Boolean.TRUE.equals(request.getFallbackToSuperAdmin())
                && requestRepository.findFallbackApproverIds().contains(checkerId)) {
            return;
        }
        throw new IllegalArgumentException(
                "You cannot approve this: it requires " + required.getName() + ", which you do not hold.");
    }

    private long escalationHours() {
        return systemConfigRepository.findLong(ESCALATION_HOURS_KEY).orElse(DEFAULT_ESCALATION_HOURS);
    }

    private long expiryDays() {
        return systemConfigRepository.findLong(EXPIRY_DAYS_KEY).orElse(DEFAULT_EXPIRY_DAYS);
    }

    private static Map<String, Object> describe(MakerCheckerRequest r, String permissionName) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestType", String.valueOf(r.getRequestType()));
        m.put("makerId", r.getMakerId());
        m.put("targetUserId", r.getTargetUserId());
        m.put("permission", permissionName);
        m.put("fallback", r.getFallbackToSuperAdmin());
        return m;
    }
}

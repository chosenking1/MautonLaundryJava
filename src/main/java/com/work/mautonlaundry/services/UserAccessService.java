package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.GrantType;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.UserPermissionRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import com.work.mautonlaundry.security.MakerCheckerService;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.security.scope.ScopeFilterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Grants, denies and revokes a user's individual permissions, and sets their
 * data scope (Permission Architecture V2, §2.2, §5.5, §7.4).
 *
 * <p>Before this, user_permissions and user_scope were writable only by
 * hand-written SQL: the evaluation engine read them, but nothing in the
 * application could change them.
 *
 * <h2>The escalation ceiling, and the way around it</h2>
 * Granting a permission you hold executes immediately. Granting one you do not
 * hold does not fail -- it opens a maker-checker request (spec §2.2), so a
 * Permission Admin can delegate anything in the system while someone who can
 * actually evaluate it signs it off. Only lacking PERMISSION_ASSIGN itself is a
 * flat refusal, and that is enforced at the endpoint.
 *
 * <p>DENY and REVOKE are deliberately exempt from both. Taking a permission away
 * is not an escalation, and requiring you to hold something before you can
 * remove it would make an over-granted permission impossible to claw back by
 * exactly the people most likely to notice.
 *
 * <h2>Not yet: pending_permission_review</h2>
 * Spec §2.1 says reducing someone's permissions should flag the grants they
 * previously made that now exceed their own set, for human review rather than
 * auto-revocation. The table exists (V10); nothing writes it yet. Left
 * deliberately, because auto-revoking would be the wrong fix and a half-built
 * flagging path is worse than none.
 */
@Service
@RequiredArgsConstructor
public class UserAccessService {

    private static final Logger log = LoggerFactory.getLogger(UserAccessService.class);

    private final UserPermissionRepository userPermissionRepository;
    private final UserScopeRepository userScopeRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final ScopeFilterService scopeFilterService;
    private final AuditService auditService;

    /** Lazy: MakerCheckerService calls grantApproved() back into this service on approval. */
    @Lazy
    private final MakerCheckerService makerCheckerService;

    /**
     * Grants a permission directly to a user, overriding their role.
     *
     * <p>Completes spec §2.2's table. If the actor holds the permission it
     * executes immediately; if not, it does NOT fail -- a maker-checker request
     * is opened instead, so a Permission Admin can delegate anything in the
     * system while someone who can actually evaluate it signs it off.
     *
     * @return the outcome; EXECUTED or PENDING_APPROVAL. Callers must not assume
     *         a non-exception means the grant happened.
     */
    @Transactional
    public GrantOutcome grant(String userId, Long permissionId, String actorId) {
        Permission permission = require(permissionId);

        if (!permissionEvaluationService.getEffectivePermissions(actorId).contains(permission.getName())) {
            // Spec §2.2 row 3: has PERMISSION_ASSIGN, lacks the target permission
            // -> maker-checker, not a refusal.
            String requestId = makerCheckerService
                    .submitPermissionAssign(actorId, userId, permissionId).getId();
            log.info("{} cannot grant {} directly; opened maker-checker request {}",
                    actorId, permission.getName(), requestId);
            return new GrantOutcome(false, requestId);
        }

        upsert(userId, permission, GrantType.GRANT, actorId);
        return new GrantOutcome(true, null);
    }

    /**
     * Executes a grant a checker has approved, bypassing the holds-check.
     *
     * <p>Not a hole: MakerCheckerService has already verified the checker
     * personally holds the permission (spec §2.3), which is a stronger guarantee
     * than the direct path's -- two people signed off instead of one. Package
     * boundaries cannot express "only reachable post-approval", so this carries
     * the requirement in its name and its javadoc.
     *
     * @param checkerId the approver, who is the one accountable for the grant
     */
    @Transactional
    public void grantApproved(String userId, Long permissionId, String checkerId) {
        upsert(userId, require(permissionId), GrantType.GRANT, checkerId);
    }

    /** @param requestId non-null only when the grant is awaiting approval */
    public record GrantOutcome(boolean executed, String requestId) {
        public boolean pendingApproval() {
            return !executed;
        }
    }

    /**
     * Denies a permission to a user. Beats every grant source -- role, module,
     * or an explicit grant (spec §2.6 step 1).
     *
     * <p>No holds-check: see the class note on de-escalation.
     */
    @Transactional
    public void deny(String userId, Long permissionId, String actorId) {
        upsert(userId, require(permissionId), GrantType.DENY, actorId);
    }

    /**
     * Removes the individual override entirely, so the user falls back to
     * whatever their role and modules give them -- which is not the same as DENY.
     */
    @Transactional
    public void revoke(String userId, Long permissionId, String actorId) {
        Permission permission = require(permissionId);
        Optional<String> existing = userPermissionRepository.findGrantType(userId, permissionId);
        if (existing.isEmpty()) {
            return;
        }
        userPermissionRepository.deleteFor(userId, permissionId);
        permissionEvaluationService.invalidate(userId);

        auditService.logChange("PERMISSION_REVOKE", "USER_PERMISSION", userId,
                Map.of("permission", permission.getName(), "grantType", existing.get()), null);
        log.info("{} revoked individual {} from {}", actorId, permission.getName(), userId);
    }

    /**
     * Sets a user's data scope.
     *
     * <p>Exactly one target must match the level, and the database enforces it
     * (V15's CHECK). Passing a state id with NATIONAL is rejected there rather
     * than silently ignored.
     */
    @Transactional
    public void assignScope(String userId, ScopeLevel level, String regionId, Integer stateId,
                            Integer lgaId, String specialistUserId, String actorId) {
        Optional<UserScope> before = userScopeRepository.findById(userId);
        // Snapshot the before-state NOW. orElseGet returns the very object about
        // to be mutated below, so reading it after would record old == new and
        // produce an audit trail that says nothing changed.
        Map<String, Object> oldView = before.map(UserAccessService::scopeView).orElse(null);

        UserScope scope = before.orElseGet(UserScope::new);
        scope.setUserId(userId);
        scope.setScopeLevel(level);
        // Clear every target first: switching STATE -> NATIONAL must not leave
        // the old state_id behind, which the CHECK would then reject.
        scope.setRegionId(null);
        scope.setStateId(null);
        scope.setLgaId(null);
        scope.setSpecialistUserId(null);
        switch (level) {
            case NATIONAL -> { }
            case REGIONAL -> scope.setRegionId(regionId);
            case STATE -> scope.setStateId(stateId);
            case ZONE -> scope.setLgaId(lgaId);
            case SPECIALIST -> scope.setSpecialistUserId(specialistUserId);
        }
        scope.setAssignedBy(actorId);
        scope.setAssignedAt(LocalDateTime.now());
        userScopeRepository.save(scope);

        scopeFilterService.invalidate(userId);
        auditService.logChange("SCOPE_ASSIGN", "USER_SCOPE", userId, oldView, scopeView(scope));
        log.info("{} set scope of {} to {}", actorId, userId, level);
    }

    // ---- internals ----

    private void upsert(String userId, Permission permission, GrantType type, String actorId) {
        Optional<String> before = userPermissionRepository.findGrantType(userId, permission.getId());

        // UNIQUE(user_id, permission_id) means a user is either granted or denied
        // a permission, never both -- so flipping one is a replace, not an insert.
        userPermissionRepository.deleteFor(userId, permission.getId());
        userPermissionRepository.insert(UUID.randomUUID().toString(), userId,
                permission.getId(), type.name(), actorId);

        permissionEvaluationService.invalidate(userId);

        Map<String, Object> oldValue = before
                .<Map<String, Object>>map(g -> Map.of("permission", permission.getName(), "grantType", g))
                .orElse(null);
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("permission", permission.getName());
        newValue.put("grantType", type.name());
        auditService.logChange("PERMISSION_" + type.name(), "USER_PERMISSION", userId, oldValue, newValue);

        log.info("{} set {} to {} for {}", actorId, permission.getName(), type, userId);
    }

    private Permission require(Long permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));
    }

    private static Map<String, Object> scopeView(UserScope s) {
        Map<String, Object> m = new HashMap<>();
        m.put("scopeLevel", String.valueOf(s.getScopeLevel()));
        if (s.getRegionId() != null) m.put("regionId", s.getRegionId());
        if (s.getStateId() != null) m.put("stateId", s.getStateId());
        if (s.getLgaId() != null) m.put("lgaId", s.getLgaId());
        if (s.getSpecialistUserId() != null) m.put("specialistUserId", s.getSpecialistUserId());
        return m;
    }
}

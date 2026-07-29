package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ScopeUpgradeRequest;
import com.work.mautonlaundry.data.model.TemporaryScopeGrant;
import com.work.mautonlaundry.data.model.Zone;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.model.enums.ScopeUpgradeStatus;
import com.work.mautonlaundry.data.repository.ScopeUpgradeRequestRepository;
import com.work.mautonlaundry.data.repository.TemporaryScopeGrantRepository;
import com.work.mautonlaundry.data.repository.ZoneRepository;
import com.work.mautonlaundry.security.scope.ScopeContext;
import com.work.mautonlaundry.security.scope.ScopeFilterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-boxed scope upgrades (Permission Architecture V2, spec §6, §8.3).
 *
 * <p>A Zone Coordinator covering a colleague's leave needs STATE visibility for a
 * fortnight, not forever. The approver sets an expiry and it reverts on its own.
 *
 * <h2>Approver eligibility, and the spec's contradiction</h2>
 * §6.1: "approved only by a user who currently holds the requested scope level
 * <em>or higher</em> for the same geographic area", illustrated with "a
 * South-West Regional Coordinator <em>cannot</em> approve [a STATE request] if
 * they are not the State-level authority for Lagos".
 *
 * <p>Those disagree: REGIONAL <em>is</em> higher than STATE, so the rule permits
 * exactly what the example forbids. Implemented here is the rule, not the
 * example: the approver's own scope must be at least as broad as the request AND
 * cover the same ground. A South-West Regional Coordinator can therefore approve
 * a Lagos STATE request -- they can already see every Lagos order themselves, so
 * they are not granting sight of anything they lack.
 *
 * <p>The example's instinct is about authority rather than visibility, which is a
 * reasonable thing to want; it is just not what the rule says, and it needs a
 * decision rather than a guess.
 *
 * <h2>Why the expiry actually works</h2>
 * The upgrade lives in the database and ScopeFilterService reads it per request,
 * so a lapse takes effect on the next call. Nothing depends on the user handing
 * the access back, and nothing depends on a token refresh -- which §6.1 assumes
 * and this codebase cannot do.
 *
 * <p>Temporary scope grants no permissions (§6.1). This service never touches
 * PermissionEvaluationService.
 */
@Service
@RequiredArgsConstructor
public class ScopeUpgradeService {

    private static final Logger log = LoggerFactory.getLogger(ScopeUpgradeService.class);

    private final ScopeUpgradeRequestRepository requestRepository;
    private final TemporaryScopeGrantRepository grantRepository;
    private final ScopeFilterService scopeFilterService;
    private final ZoneRepository zoneRepository;
    private final AuditService auditService;

    /** §6.1: any user may ask. Whether anyone can approve is a separate matter. */
    @Transactional
    public ScopeUpgradeRequest requestUpgrade(String requesterId, ScopeLevel level, String regionId,
                                              Integer stateId, String zoneId, String reason) {
        ScopeContext current = scopeFilterService.scopeFor(requesterId);

        ScopeUpgradeRequest request = new ScopeUpgradeRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequesterId(requesterId);
        // Captured now: the approver decides about a specific delta, and the
        // requester's permanent scope may move while the request sits.
        request.setCurrentScopeLevel(current.denied() ? "NONE" : String.valueOf(current.level()));
        request.setCurrentScopeValue(describeCurrent(current));
        request.setRequestedScopeLevel(level);
        request.setRequestedRegionId(regionId);
        request.setRequestedStateId(stateId);
        request.setRequestedZoneId(zoneId);
        request.setReason(reason);
        request.setStatus(ScopeUpgradeStatus.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        requestRepository.save(request);

        log.info("{} requested temporary {} scope: {}", requesterId, level, reason);
        return request;
    }

    /**
     * Approves and opens the window.
     *
     * @param expiry when it lapses; must be in the future. §6.1 permits no
     *               permanent upgrade through this mechanism, which V22's CHECK
     *               also enforces.
     */
    @Transactional
    public void approveUpgrade(String requestId, String approverId, LocalDateTime expiry) {
        ScopeUpgradeRequest request = requirePending(requestId);

        if (expiry == null || expiry.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("A temporary upgrade needs an expiry in the future.");
        }
        if (request.getRequesterId().equals(approverId)) {
            throw new IllegalArgumentException("You cannot approve your own scope upgrade.");
        }
        requireApproverCovers(request, approverId);

        request.setStatus(ScopeUpgradeStatus.APPROVED);
        request.setReviewedBy(approverId);
        request.setReviewedAt(LocalDateTime.now());
        request.setExpiry(expiry);
        requestRepository.save(request);

        TemporaryScopeGrant grant = new TemporaryScopeGrant();
        grant.setId(UUID.randomUUID().toString());
        grant.setUserId(request.getRequesterId());
        grant.setScopeUpgradeRequestId(request.getId());
        grant.setTemporaryScopeLevel(request.getRequestedScopeLevel());
        grant.setTemporaryRegionId(request.getRequestedRegionId());
        grant.setTemporaryStateId(request.getRequestedStateId());
        grant.setTemporaryZoneId(request.getRequestedZoneId());
        grant.setGrantedBy(approverId);
        grant.setGrantedAt(LocalDateTime.now());
        grant.setExpiresAt(expiry);
        grant.setIsActive(true);
        grantRepository.save(grant);

        // Their cached scope is now wrong in their favour; drop it so the wider
        // view starts on the next request rather than up to a TTL later.
        scopeFilterService.invalidate(request.getRequesterId());

        Map<String, Object> after = new HashMap<>();
        after.put("scopeLevel", String.valueOf(request.getRequestedScopeLevel()));
        after.put("expiry", String.valueOf(expiry));
        after.put("approvedBy", approverId);
        auditService.logChange("SCOPE_UPGRADE_APPROVE", "USER_SCOPE", request.getRequesterId(),
                Map.of("scopeLevel", String.valueOf(request.getCurrentScopeLevel())), after);

        log.info("{} approved {} temporary {} scope until {}",
                approverId, request.getRequesterId(), request.getRequestedScopeLevel(), expiry);
    }

    @Transactional
    public void rejectUpgrade(String requestId, String approverId, String reason) {
        ScopeUpgradeRequest request = requirePending(requestId);
        requireApproverCovers(request, approverId);

        request.setStatus(ScopeUpgradeStatus.REJECTED);
        request.setReviewedBy(approverId);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        requestRepository.save(request);

        log.info("{} rejected {}'s scope upgrade: {}", approverId, request.getRequesterId(), reason);
    }

    /**
     * Closes lapsed upgrades (spec §8.3, every 15 minutes).
     *
     * <p>Correctness does not depend on this running: ScopeFilterService checks
     * expires_at as well as is_active, so an upgrade stops applying the moment it
     * lapses. The sweep makes the state tidy and auditable, and drops the cached
     * set so nobody keeps a wider view for the rest of the TTL.
     *
     * @return how many were reverted
     */
    @Transactional
    public int revertExpiredScopes() {
        List<TemporaryScopeGrant> lapsed = grantRepository.findLapsed(LocalDateTime.now());
        for (TemporaryScopeGrant g : lapsed) {
            g.setIsActive(false);
            g.setRevertedAt(LocalDateTime.now());
            grantRepository.save(g);

            requestRepository.findById(g.getScopeUpgradeRequestId()).ifPresent(r -> {
                r.setStatus(ScopeUpgradeStatus.EXPIRED);
                requestRepository.save(r);
            });

            scopeFilterService.invalidate(g.getUserId());
            auditService.logChange("SCOPE_UPGRADE_REVERT", "USER_SCOPE", g.getUserId(),
                    Map.of("scopeLevel", String.valueOf(g.getTemporaryScopeLevel())),
                    Map.of("scopeLevel", "PERMANENT"));
            log.info("Reverted {}'s temporary {} scope (expired {})",
                    g.getUserId(), g.getTemporaryScopeLevel(), g.getExpiresAt());
        }
        return lapsed.size();
    }

    // ---- internals ----

    /**
     * §6.1: the approver's own scope must be at least as broad as the request and
     * cover the same ground. You cannot hand out a view you do not have.
     */
    private void requireApproverCovers(ScopeUpgradeRequest request, String approverId) {
        ScopeContext approver = scopeFilterService.scopeFor(approverId);
        if (approver.denied()) {
            throw new IllegalArgumentException("You have no scope, so you cannot approve one.");
        }
        if (approver.isUnrestricted()) {
            return;   // NATIONAL covers everything
        }

        boolean covers = switch (request.getRequestedScopeLevel()) {
            case NATIONAL -> false;   // only NATIONAL can hand out NATIONAL
            case REGIONAL -> false;   // a region spans states the approver would need to already hold
            case STATE -> request.getRequestedStateId() != null
                    && approver.stateIds().contains(request.getRequestedStateId());
            // A zone is a set of LGAs. The approver covers it if they can already
            // see all of it: a state-scoped approver whose state contains the
            // zone, or a zone-scoped approver whose own LGA set is a superset.
            case ZONE -> approverCoversZone(approver, request.getRequestedZoneId());
            case SPECIALIST -> false;
        };
        if (!covers) {
            throw new IllegalArgumentException(
                    "You cannot approve this: your own scope does not cover "
                            + request.getRequestedScopeLevel() + " for that area.");
        }
    }

    /**
     * True when the approver can already see the whole of the requested zone.
     *
     * <p>Either their scope covers the zone's state (a state head can approve any
     * zone within their state), or -- for a zone-scoped approver -- their own LGA
     * set contains every LGA of the requested zone.
     */
    private boolean approverCoversZone(ScopeContext approver, String requestedZoneId) {
        if (requestedZoneId == null) {
            return false;
        }
        Zone zone = zoneRepository.findById(requestedZoneId).orElse(null);
        if (zone == null) {
            return false;
        }
        if (approver.stateIds().contains(zone.getState().getId())) {
            return true;
        }
        List<Integer> zoneLgas = zoneRepository.findLgaIds(requestedZoneId);
        return !zoneLgas.isEmpty() && approver.lgaIds().containsAll(zoneLgas);
    }

    private ScopeUpgradeRequest requirePending(String requestId) {
        ScopeUpgradeRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        if (r.getStatus() != ScopeUpgradeStatus.PENDING) {
            throw new IllegalArgumentException("Request is already " + r.getStatus() + ".");
        }
        return r;
    }

    private static String describeCurrent(ScopeContext c) {
        if (c.denied()) return null;
        if (!c.lgaIds().isEmpty()) return "zone-lgas:" + c.lgaIds();
        if (!c.stateIds().isEmpty()) return "states:" + c.stateIds();
        return null;
    }
}

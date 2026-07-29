package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.MakerCheckerRequest;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.model.PendingPermissionReview;
import com.work.mautonlaundry.data.repository.PendingPermissionReviewRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.UserPermissionRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import com.work.mautonlaundry.security.MakerCheckerService;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.security.scope.ScopeFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAccessServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String TARGET = "user-9";

    @Mock private UserPermissionRepository userPermissionRepository;
    @Mock private UserScopeRepository userScopeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PendingPermissionReviewRepository pendingReviewRepository;
    @Mock private PermissionEvaluationService permissionEvaluationService;
    @Mock private ScopeFilterService scopeFilterService;
    @Mock private AuditService auditService;
    @Mock private MakerCheckerService makerCheckerService;

    @InjectMocks private UserAccessService service;

    private Permission payoutView;
    private Permission payoutSettle;

    @BeforeEach
    void setUp() {
        payoutView = perm(1L, "PAYOUT_VIEW");
        payoutSettle = perm(9L, "PAYOUT_SETTLE");
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(payoutView));
        when(permissionRepository.findById(9L)).thenReturn(Optional.of(payoutSettle));
        when(permissionEvaluationService.getEffectivePermissions(ACTOR)).thenReturn(Set.of("PAYOUT_VIEW"));
        when(userPermissionRepository.findGrantType(anyString(), anyLong())).thenReturn(Optional.empty());
        when(userScopeRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userScopeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pendingReviewRepository.findGrantsMadeBy(anyString())).thenReturn(java.util.List.of());
    }

    private Permission perm(long id, String name) {
        Permission p = new Permission();
        p.setId(id);
        p.setName(name);
        return p;
    }

    // ---- spec §2.2's table ----

    @Test
    void grantingAPermissionTheActorLacksOpensMakerCheckerRatherThanFailing() {
        // §2.2 row 3. The point of the mechanism is that a Permission Admin can
        // delegate anything in the system WITHOUT holding it -- refusing would
        // defeat that; executing would be the escalation.
        MakerCheckerRequest opened = new MakerCheckerRequest();
        opened.setId("req-1");
        when(makerCheckerService.submitPermissionAssign(ACTOR, TARGET, 9L)).thenReturn(opened);

        UserAccessService.GrantOutcome outcome = service.grant(TARGET, 9L, ACTOR);

        assertThat(outcome.pendingApproval()).isTrue();
        assertThat(outcome.requestId()).isEqualTo("req-1");
        // Nothing granted yet -- that is the whole point.
        verify(userPermissionRepository, never()).insert(anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(permissionEvaluationService, never()).invalidate(TARGET);
    }

    @Test
    void grantingWhatTheActorHoldsExecutesImmediately() {
        // §2.2 row 2: no approval needed to delegate what you already have.
        UserAccessService.GrantOutcome outcome = service.grant(TARGET, 1L, ACTOR);

        assertThat(outcome.executed()).isTrue();
        assertThat(outcome.requestId()).isNull();
        verify(userPermissionRepository).insert(anyString(), eq(TARGET), eq(1L), eq("GRANT"), eq(ACTOR));
        verify(permissionEvaluationService).invalidate(TARGET);
        verify(makerCheckerService, never()).submitPermissionAssign(anyString(), anyString(), anyLong());
    }

    @Test
    void grantApprovedExecutesWithoutAHoldsCheckAndCreditsTheChecker() {
        // Reached only after MakerCheckerService verified the checker holds it,
        // which is a stronger guarantee than the direct path's.
        service.grantApproved(TARGET, 9L, "checker-1");

        verify(userPermissionRepository).insert(anyString(), eq(TARGET), eq(9L), eq("GRANT"), eq("checker-1"));
        verify(permissionEvaluationService).invalidate(TARGET);
    }

    @Test
    void denyNeedsNoHoldsCheck() {
        // De-escalation. Requiring you to hold a permission before removing it
        // would make an over-granted permission un-clawback-able by exactly the
        // people most likely to spot it.
        service.deny(TARGET, 9L, ACTOR);   // actor does NOT hold PAYOUT_SETTLE

        verify(userPermissionRepository).insert(anyString(), eq(TARGET), eq(9L), eq("DENY"), eq(ACTOR));
        verify(permissionEvaluationService).invalidate(TARGET);
    }

    @Test
    void revokeNeedsNoHoldsCheckEither() {
        when(userPermissionRepository.findGrantType(TARGET, 9L)).thenReturn(Optional.of("GRANT"));

        service.revoke(TARGET, 9L, ACTOR);

        verify(userPermissionRepository).deleteFor(TARGET, 9L);
        verify(permissionEvaluationService).invalidate(TARGET);
    }

    @Test
    void revokingSomethingNotSetIsANoOp() {
        service.revoke(TARGET, 1L, ACTOR);

        verify(userPermissionRepository, never()).deleteFor(anyString(), anyLong());
        verify(auditService, never()).logChange(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void flippingGrantToDenyReplacesRatherThanDuplicates() {
        // UNIQUE(user_id, permission_id): a user is granted or denied, never both.
        when(userPermissionRepository.findGrantType(TARGET, 1L)).thenReturn(Optional.of("GRANT"));

        service.deny(TARGET, 1L, ACTOR);

        verify(userPermissionRepository).deleteFor(TARGET, 1L);
        verify(userPermissionRepository).insert(anyString(), eq(TARGET), eq(1L), eq("DENY"), eq(ACTOR));
    }

    // ---- audit (spec §8.5) ----

    @Test
    void grantIsAuditedWithBeforeAndAfter() {
        when(userPermissionRepository.findGrantType(TARGET, 1L)).thenReturn(Optional.of("DENY"));

        service.grant(TARGET, 1L, ACTOR);

        ArgumentCaptor<Map<String, Object>> oldV = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> newV = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logChange(eq("PERMISSION_GRANT"), eq("USER_PERMISSION"), eq(TARGET),
                oldV.capture(), newV.capture());
        assertThat(oldV.getValue()).containsEntry("grantType", "DENY");
        assertThat(newV.getValue()).containsEntry("grantType", "GRANT");
    }

    @Test
    void firstGrantHasNoBeforeState() {
        service.grant(TARGET, 1L, ACTOR);

        ArgumentCaptor<Map<String, Object>> oldV = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logChange(anyString(), anyString(), anyString(), oldV.capture(), any());
        assertThat(oldV.getValue()).isNull();
    }

    // ---- scope (spec §5.5) ----

    @Test
    void assigningScopeSetsOnlyTheMatchingTarget() {
        service.assignScope(TARGET, ScopeLevel.STATE, null, 25, null, null, ACTOR);

        ArgumentCaptor<UserScope> saved = ArgumentCaptor.forClass(UserScope.class);
        verify(userScopeRepository).save(saved.capture());
        assertThat(saved.getValue().getScopeLevel()).isEqualTo(ScopeLevel.STATE);
        assertThat(saved.getValue().getStateId()).isEqualTo(25);
        assertThat(saved.getValue().getZoneId()).isNull();
        assertThat(saved.getValue().getRegionId()).isNull();
        verify(scopeFilterService).invalidate(TARGET);
    }

    @Test
    void switchingToNationalClearsThePreviousTarget() {
        // Otherwise the leftover state_id would violate V15's level/target CHECK.
        UserScope existing = new UserScope();
        existing.setUserId(TARGET);
        existing.setScopeLevel(ScopeLevel.STATE);
        existing.setStateId(25);
        when(userScopeRepository.findById(TARGET)).thenReturn(Optional.of(existing));

        service.assignScope(TARGET, ScopeLevel.NATIONAL, null, null, null, null, ACTOR);

        ArgumentCaptor<UserScope> saved = ArgumentCaptor.forClass(UserScope.class);
        verify(userScopeRepository).save(saved.capture());
        assertThat(saved.getValue().getScopeLevel()).isEqualTo(ScopeLevel.NATIONAL);
        assertThat(saved.getValue().getStateId()).isNull();
    }

    @Test
    void scopeChangeIsAuditedWithBeforeAndAfter() {
        UserScope existing = new UserScope();
        existing.setUserId(TARGET);
        existing.setScopeLevel(ScopeLevel.NATIONAL);
        when(userScopeRepository.findById(TARGET)).thenReturn(Optional.of(existing));

        service.assignScope(TARGET, ScopeLevel.ZONE, null, null, "zone-1", null, ACTOR);

        ArgumentCaptor<Map<String, Object>> oldV = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> newV = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logChange(eq("SCOPE_ASSIGN"), eq("USER_SCOPE"), eq(TARGET),
                oldV.capture(), newV.capture());
        assertThat(oldV.getValue()).containsEntry("scopeLevel", "NATIONAL");
        assertThat(newV.getValue()).containsEntry("scopeLevel", "ZONE").containsEntry("zoneId", "zone-1");
    }

    @Test
    void scopeUsesTheScopeCacheNotThePermissionCache() {
        // They are separate caches; invalidating the wrong one leaves the old
        // scope live for the TTL.
        service.assignScope(TARGET, ScopeLevel.NATIONAL, null, null, null, null, ACTOR);

        verify(scopeFilterService).invalidate(TARGET);
        verify(permissionEvaluationService, never()).invalidate(anyString());
    }

    @Test
    void unknownPermissionIsRejected() {
        when(permissionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(TARGET, 404L, ACTOR))
                .hasMessageContaining("Permission not found");
    }

    // ---- §2.1: reducing someone flags the grants they made ----

    @Test
    void reducingSomeoneFlagsTheGrantsTheyCanNoLongerMake() {
        // TARGET granted PAYOUT_SETTLE to two people; now TARGET is denied it.
        when(permissionEvaluationService.getEffectivePermissions(TARGET)).thenReturn(Set.of("PAYOUT_VIEW"));
        when(pendingReviewRepository.findGrantsMadeBy(TARGET))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"recipient-1", 9L}, new Object[]{"recipient-2", 9L}));

        service.deny(TARGET, 9L, ACTOR);

        ArgumentCaptor<PendingPermissionReview> flagged = ArgumentCaptor.forClass(PendingPermissionReview.class);
        verify(pendingReviewRepository, org.mockito.Mockito.times(2)).save(flagged.capture());
        assertThat(flagged.getAllValues()).extracting(PendingPermissionReview::getUserId)
                .containsExactlyInAnyOrder("recipient-1", "recipient-2");
        assertThat(flagged.getAllValues()).allSatisfy(r ->
                assertThat(r.getOriginalGrantorId()).isEqualTo(TARGET));
    }

    @Test
    void flaggingNeverRevokesTheRecipientsGrant() {
        // §2.1 is explicit: NOT auto-revoked. The recipient may need it to work,
        // and their grantor changing team is not a reason to break them.
        when(permissionEvaluationService.getEffectivePermissions(TARGET)).thenReturn(Set.of());
        when(pendingReviewRepository.findGrantsMadeBy(TARGET))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"recipient-1", 9L}));

        service.deny(TARGET, 9L, ACTOR);

        verify(userPermissionRepository, never()).deleteFor(eq("recipient-1"), anyLong());
        verify(permissionEvaluationService, never()).invalidate("recipient-1");
    }

    @Test
    void grantsTheyCanStillMakeAreNotFlagged() {
        when(permissionEvaluationService.getEffectivePermissions(TARGET)).thenReturn(Set.of("PAYOUT_VIEW"));
        when(pendingReviewRepository.findGrantsMadeBy(TARGET))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"recipient-1", 1L}));   // PAYOUT_VIEW, still held

        service.deny(TARGET, 9L, ACTOR);

        verify(pendingReviewRepository, never()).save(any());
    }

    @Test
    void reducingTwiceDoesNotQueueTheSameGrantTwice() {
        when(permissionEvaluationService.getEffectivePermissions(TARGET)).thenReturn(Set.of());
        when(pendingReviewRepository.findGrantsMadeBy(TARGET))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"recipient-1", 9L}));
        when(pendingReviewRepository.countOpen("recipient-1", 9L, TARGET)).thenReturn(1L);

        service.deny(TARGET, 9L, ACTOR);

        verify(pendingReviewRepository, never()).save(any());
    }

    @Test
    void resolvingAsRevokedRemovesTheRecipientsGrant() {
        PendingPermissionReview r = new PendingPermissionReview();
        r.setId("rev-1");
        r.setUserId("recipient-1");
        r.setPermissionId(9L);
        r.setOriginalGrantorId(TARGET);
        when(pendingReviewRepository.findById("rev-1")).thenReturn(Optional.of(r));

        service.resolveReview("rev-1", false, "reviewer-1");

        assertThat(r.getResolution()).isEqualTo("REVOKED");
        verify(userPermissionRepository).deleteFor("recipient-1", 9L);
        verify(permissionEvaluationService).invalidate("recipient-1");
    }

    @Test
    void resolvingAsKeptLeavesTheGrantAlone() {
        PendingPermissionReview r = new PendingPermissionReview();
        r.setId("rev-1");
        r.setUserId("recipient-1");
        r.setPermissionId(9L);
        r.setOriginalGrantorId(TARGET);
        when(pendingReviewRepository.findById("rev-1")).thenReturn(Optional.of(r));

        service.resolveReview("rev-1", true, "reviewer-1");

        assertThat(r.getResolution()).isEqualTo("KEPT");
        verify(userPermissionRepository, never()).deleteFor(anyString(), anyLong());
    }

    @Test
    void aResolvedReviewCannotBeResolvedAgain() {
        PendingPermissionReview r = new PendingPermissionReview();
        r.setId("rev-1");
        r.setReviewedAt(java.time.LocalDateTime.now());
        when(pendingReviewRepository.findById("rev-1")).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.resolveReview("rev-1", true, "reviewer-1"))
                .hasMessageContaining("already resolved");
    }
}

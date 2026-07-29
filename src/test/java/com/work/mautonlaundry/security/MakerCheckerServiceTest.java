package com.work.mautonlaundry.security;

import com.work.mautonlaundry.data.model.MakerCheckerRequest;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.enums.MakerCheckerStatus;
import com.work.mautonlaundry.data.repository.MakerCheckerRequestRepository;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.SystemConfigRepository;
import com.work.mautonlaundry.services.AuditService;
import com.work.mautonlaundry.services.UserAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
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

/**
 * Checker eligibility (spec §2.3) and the request lifecycle (§8.4).
 *
 * <p>Eligibility carries the entire security property: without it, two equally
 * uninformed admins can rubber-stamp each other into anything.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MakerCheckerServiceTest {

    private static final String MAKER = "maker-1";
    private static final String CHECKER = "checker-1";
    private static final String TARGET = "user-9";
    private static final long PAYOUT_SETTLE = 9L;

    @Mock private MakerCheckerRequestRepository requestRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private PermissionEvaluationService permissionEvaluationService;
    @Mock private AuditService auditService;
    @Mock private UserAccessService userAccessService;

    @InjectMocks private MakerCheckerService service;

    private Permission payoutSettle;

    @BeforeEach
    void setUp() {
        payoutSettle = new Permission();
        payoutSettle.setId(PAYOUT_SETTLE);
        payoutSettle.setName("PAYOUT_SETTLE");
        when(permissionRepository.findById(PAYOUT_SETTLE)).thenReturn(Optional.of(payoutSettle));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(systemConfigRepository.findLong(anyString())).thenReturn(Optional.empty());
        when(requestRepository.findEligibleCheckerIds(PAYOUT_SETTLE)).thenReturn(List.of(CHECKER));
    }

    private MakerCheckerRequest pending() {
        MakerCheckerRequest r = new MakerCheckerRequest();
        r.setId("req-1");
        r.setMakerId(MAKER);
        r.setTargetUserId(TARGET);
        r.setTargetPermissionId(PAYOUT_SETTLE);
        r.setRequiredCheckerPermissionId(PAYOUT_SETTLE);
        r.setStatus(MakerCheckerStatus.PENDING);
        r.setFallbackToSuperAdmin(false);
        r.setRequestType(com.work.mautonlaundry.data.model.enums.MakerCheckerType.PERMISSION_ASSIGN);
        when(requestRepository.findById("req-1")).thenReturn(Optional.of(r));
        return r;
    }

    private void checkerHolds(String... names) {
        when(permissionEvaluationService.getEffectivePermissions(CHECKER)).thenReturn(Set.of(names));
    }

    // ---- submission ----

    @Test
    void submissionRecordsWhatIsNeededToApproveIt() {
        MakerCheckerRequest r = service.submitPermissionAssign(MAKER, TARGET, PAYOUT_SETTLE);

        assertThat(r.getStatus()).isEqualTo(MakerCheckerStatus.PENDING);
        assertThat(r.getRequiredCheckerPermissionId()).isEqualTo(PAYOUT_SETTLE);
        assertThat(r.getFallbackToSuperAdmin()).isFalse();
        assertThat(r.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void noEligibleCheckerFlagsFallbackImmediatelyRatherThanWaiting() {
        // Spec §8.4: waiting out the escalation window for a decision nobody can
        // make would just delay it.
        when(requestRepository.findEligibleCheckerIds(PAYOUT_SETTLE)).thenReturn(List.of());

        assertThat(service.submitPermissionAssign(MAKER, TARGET, PAYOUT_SETTLE)
                .getFallbackToSuperAdmin()).isTrue();
    }

    // ---- eligibility: the security property (spec §2.3) ----

    @Test
    void aCheckerWhoLacksThePermissionCannotApprove() {
        pending();
        checkerHolds("ORDER_VIEW");   // not PAYOUT_SETTLE

        assertThatThrownBy(() -> service.approve("req-1", CHECKER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYOUT_SETTLE");

        verify(userAccessService, never()).grantApproved(anyString(), anyLong(), anyString());
    }

    @Test
    void twoIneligiblePeopleDoNotAddUpToOneEligibleOne() {
        // The check is per-person, never aggregate (spec §2.3).
        pending();
        checkerHolds("ORDER_VIEW");
        when(permissionEvaluationService.getEffectivePermissions("checker-2")).thenReturn(Set.of("ORDER_EDIT"));

        assertThatThrownBy(() -> service.approve("req-1", CHECKER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.approve("req-1", "checker-2")).isInstanceOf(IllegalArgumentException.class);
        verify(userAccessService, never()).grantApproved(anyString(), anyLong(), anyString());
    }

    @Test
    void aCheckerWhoHoldsThePermissionApprovesAndTheGrantExecutes() {
        pending();
        checkerHolds("PAYOUT_SETTLE");

        service.approve("req-1", CHECKER);

        verify(userAccessService).grantApproved(TARGET, PAYOUT_SETTLE, CHECKER);
    }

    @Test
    void theMakerCannotApproveTheirOwnRequest() {
        // A second pair of eyes cannot be the first pair -- even if the maker
        // somehow holds the permission.
        pending();
        when(permissionEvaluationService.getEffectivePermissions(MAKER)).thenReturn(Set.of("PAYOUT_SETTLE"));

        assertThatThrownBy(() -> service.approve("req-1", MAKER))
                .hasMessageContaining("cannot approve your own");

        verify(userAccessService, never()).grantApproved(anyString(), anyLong(), anyString());
    }

    @Test
    void fallbackApproverCanApproveOnlyWhenTheRequestWasFlaggedForFallback() {
        MakerCheckerRequest r = pending();
        r.setFallbackToSuperAdmin(true);
        checkerHolds("SOMETHING_ELSE");
        when(requestRepository.findFallbackApproverIds()).thenReturn(List.of(CHECKER));

        service.approve("req-1", CHECKER);

        verify(userAccessService).grantApproved(TARGET, PAYOUT_SETTLE, CHECKER);
    }

    @Test
    void fallbackApproverCannotApproveARequestSomeoneElseCouldHandle() {
        // Not flagged for fallback: an eligible checker existed, so the fallback
        // must not be a way around them.
        pending();   // fallbackToSuperAdmin = false
        checkerHolds("SOMETHING_ELSE");
        when(requestRepository.findFallbackApproverIds()).thenReturn(List.of(CHECKER));

        assertThatThrownBy(() -> service.approve("req-1", CHECKER))
                .hasMessageContaining("PAYOUT_SETTLE");
    }

    // ---- lifecycle ----

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        MakerCheckerRequest r = pending();
        r.setStatus(MakerCheckerStatus.APPROVED);
        checkerHolds("PAYOUT_SETTLE");

        assertThatThrownBy(() -> service.approve("req-1", CHECKER))
                .hasMessageContaining("already APPROVED");
    }

    @Test
    void rejectionRecordsTheReasonAndExecutesNothing() {
        pending();
        checkerHolds("PAYOUT_SETTLE");

        service.reject("req-1", CHECKER, "Not needed for this role");

        verify(userAccessService, never()).grantApproved(anyString(), anyLong(), anyString());
        verify(auditService).logChange(eq("MAKER_CHECKER_REJECT"), anyString(), eq("req-1"), any(), any());
    }

    @Test
    void rejectionAlsoRequiresEligibility() {
        // Someone who cannot evaluate it cannot veto it either.
        pending();
        checkerHolds("ORDER_VIEW");

        assertThatThrownBy(() -> service.reject("req-1", CHECKER, "no"))
                .hasMessageContaining("PAYOUT_SETTLE");
    }

    @Test
    void escalationStampsOnceAndOnlyOnce() {
        MakerCheckerRequest r = pending();
        when(requestRepository.findNeedingEscalation(any())).thenReturn(List.of(r));

        assertThat(service.processEscalations()).isEqualTo(1);
        assertThat(r.getEscalatedAt()).isNotNull();
        // The query excludes anything already stamped, so a second sweep is a
        // no-op rather than a repeat notification.
    }

    @Test
    void expiryClosesTheRequestWithoutAChecker() {
        MakerCheckerRequest r = pending();
        when(requestRepository.findExpired(any())).thenReturn(List.of(r));

        assertThat(service.expireStale()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(MakerCheckerStatus.EXPIRED);
        assertThat(r.getCheckerId()).isNull();   // nobody decided it; V21's CHECK requires this
        verify(userAccessService, never()).grantApproved(anyString(), anyLong(), anyString());
    }

    @Test
    void configuredWindowsOverrideTheDefaults() {
        when(systemConfigRepository.findLong(MakerCheckerService.EXPIRY_DAYS_KEY)).thenReturn(Optional.of(2L));

        MakerCheckerRequest r = service.submitPermissionAssign(MAKER, TARGET, PAYOUT_SETTLE);

        assertThat(r.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(3));
    }
}

package com.work.mautonlaundry.security;

import com.work.mautonlaundry.data.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Definition of Done rows "Permission evaluation" and "Cache
 * invalidation" (Permission Architecture V2, spec §11).
 *
 * <p>Scope limit worth knowing: these mock PermissionRepository, so they verify
 * the service's set arithmetic and cache behaviour only. They do NOT verify the
 * native SQL in findGrantedPermissionNames -- which is where module exclusions
 * ("exclusion always wins") are actually enforced, via NOT EXISTS. Native
 * queries are also not validated at Spring startup the way JPQL is, so that SQL
 * needs an integration test against a real database before cutover.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionEvaluationServiceTest {

    private static final String USER = "user-1";

    @Mock private PermissionRepository permissionRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private PermissionEvaluationService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Default: cache always misses, so each test exercises the database path.
        when(valueOperations.get(anyString())).thenReturn(null);
        granted();
        denied();
    }

    private void granted(String... names) {
        when(permissionRepository.findGrantedPermissionNames(USER)).thenReturn(List.of(names));
    }

    private void denied(String... names) {
        when(permissionRepository.findDeniedPermissionNames(USER)).thenReturn(List.of(names));
    }

    // ---- closed by default (spec §2.1) ----

    @Test
    void missingRow_isDenied() {
        assertThat(service.hasPermission(USER, "PAYOUT_SETTLE")).isFalse();
        assertThat(service.getEffectivePermissions(USER)).isEmpty();
    }

    @Test
    void permissionNotInGrantedSet_isDenied() {
        granted("ORDER_VIEW");
        assertThat(service.hasPermission(USER, "PAYOUT_SETTLE")).isFalse();
    }

    @Test
    void nullUser_isDenied() {
        assertThat(service.hasPermission(null, "ORDER_VIEW")).isFalse();
        assertThat(service.getEffectivePermissions(null)).isEmpty();
    }

    @Test
    void blankUser_isDenied() {
        assertThat(service.hasPermission("   ", "ORDER_VIEW")).isFalse();
    }

    @Test
    void nullOrBlankPermissionKey_isDenied() {
        granted("ORDER_VIEW");
        assertThat(service.hasPermission(USER, null)).isFalse();
        assertThat(service.hasPermission(USER, "  ")).isFalse();
    }

    // ---- evaluation order (spec §2.6 / §8.1) ----

    @Test
    void grantedPermission_isAllowed() {
        granted("ORDER_VIEW");
        assertThat(service.hasPermission(USER, "ORDER_VIEW")).isTrue();
    }

    @Test
    void explicitDeny_beatsAnyGrantSource() {
        // The grant query unions user, role and module grants; a user-level DENY
        // must beat all of them at once.
        granted("PAYOUT_VIEW", "ORDER_VIEW");
        denied("PAYOUT_VIEW");

        assertThat(service.hasPermission(USER, "PAYOUT_VIEW")).isFalse();
        assertThat(service.hasPermission(USER, "ORDER_VIEW")).isTrue();
    }

    @Test
    void denyOfUnheldPermission_changesNothing() {
        granted("ORDER_VIEW");
        denied("PAYOUT_SETTLE");
        assertThat(service.getEffectivePermissions(USER)).containsExactly("ORDER_VIEW");
    }

    @Test
    void effectiveSet_isGrantsMinusDenies() {
        granted("A", "B", "C");
        denied("B");
        assertThat(service.getEffectivePermissions(USER)).containsExactlyInAnyOrder("A", "C");
    }

    // ---- cache behaviour (spec §8.1) ----

    @Test
    void cacheMiss_resolvesFromDatabaseAndWritesWithFiveMinuteTtl() {
        granted("ORDER_VIEW");

        service.getEffectivePermissions(USER);

        verify(valueOperations).set(eq("perms:v1:" + USER), eq("ORDER_VIEW"), eq(Duration.ofMinutes(5)));
        assertThat(PermissionEvaluationService.CACHE_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void cacheHit_doesNotTouchDatabase() {
        when(valueOperations.get("perms:v1:" + USER)).thenReturn("ORDER_VIEW\nPAYOUT_VIEW");

        assertThat(service.getEffectivePermissions(USER))
                .containsExactlyInAnyOrder("ORDER_VIEW", "PAYOUT_VIEW");
        verify(permissionRepository, never()).findGrantedPermissionNames(anyString());
        verify(permissionRepository, never()).findDeniedPermissionNames(anyString());
    }

    @Test
    void cachedEmptySet_isHonouredAndNotMistakenForCacheMiss() {
        // A user who holds nothing is a real state; caching it must not fall
        // through to the database on every request.
        when(valueOperations.get("perms:v1:" + USER)).thenReturn("");

        assertThat(service.getEffectivePermissions(USER)).isEmpty();
        verify(permissionRepository, never()).findGrantedPermissionNames(anyString());
    }

    @Test
    void emptySet_roundTripsThroughEncoding() {
        service.getEffectivePermissions(USER);
        // Encodes to "" rather than a value that would decode to a set holding
        // one empty-string permission.
        verify(valueOperations).set(eq("perms:v1:" + USER), eq(""), any(Duration.class));
    }

    @Test
    void redisReadFailure_fallsBackToDatabaseRatherThanDenying() {
        // Redis being down must not lock everyone out, nor let anyone in: the
        // database stays authoritative.
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));
        granted("ORDER_VIEW");

        assertThat(service.hasPermission(USER, "ORDER_VIEW")).isTrue();
        assertThat(service.hasPermission(USER, "PAYOUT_SETTLE")).isFalse();
    }

    @Test
    void databaseResolutionFailure_deniesClosedInsteadOfPropagating() {
        // The permissions schema not existing yet (or any DB fault) must not turn
        // every @PreAuthorize check and /me into a 500. It resolves to "holds
        // nothing" -- a clean 403 at the gate -- and never throws or widens access.
        when(permissionRepository.findGrantedPermissionNames(USER))
                .thenThrow(new RuntimeException("relation \"user_permissions\" does not exist"));

        assertThat(service.getEffectivePermissions(USER)).isEmpty();
        assertThat(service.hasPermission(USER, "BOOKING_CREATE")).isFalse();
    }

    @Test
    void databaseResolutionFailure_isNotCached_soRecoveryTakesEffectImmediately() {
        // A failure-derived empty set must never be written to the cache; doing so
        // would pin the user empty for the whole TTL even after the DB recovers.
        when(permissionRepository.findGrantedPermissionNames(USER))
                .thenThrow(new RuntimeException("connection reset"));

        service.getEffectivePermissions(USER);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisWriteFailure_stillReturnsResolvedPermissions() {
        doThrow(new RuntimeException("read only replica"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        granted("ORDER_VIEW");

        assertThat(service.hasPermission(USER, "ORDER_VIEW")).isTrue();
    }

    @Test
    void invalidate_deletesTheUsersKey() {
        service.invalidate(USER);
        verify(redisTemplate).delete("perms:v1:" + USER);
    }

    @Test
    void invalidateFailure_isSwallowedSoTheMutationSurvives() {
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("down"));
        service.invalidate(USER);  // must not propagate
    }

    @Test
    void invalidateNullUser_isNoOp() {
        service.invalidate(null);
        verify(redisTemplate, never()).delete(anyString());
    }
}

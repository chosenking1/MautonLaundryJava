package com.work.mautonlaundry.security.scope;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.TemporaryScopeGrant;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.TemporaryScopeGrantRepository;
import com.work.mautonlaundry.data.repository.ZoneRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scope resolution: what a user's scope row means, and what happens when there
 * isn't one. The Specification predicates themselves need a real EntityManager,
 * so they are exercised by the criteria at wiring time, not here -- what these
 * cover is the decision that feeds them, which is where a mistake silently
 * widens access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeFilterServiceTest {

    private static final String USER = "user-1";

    @Mock private UserScopeRepository userScopeRepository;
    @Mock private TemporaryScopeGrantRepository temporaryScopeGrantRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private ScopeFilterService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);  // cache always misses
        // No temporary upgrade unless a test says otherwise.
        when(temporaryScopeGrantRepository.findActiveFor(anyString(), any()))
                .thenReturn(Optional.empty());
        // A zone resolves to its LGA set; empty unless a test says otherwise.
        when(zoneRepository.findLgaIds(anyString())).thenReturn(List.of());
    }

    private UserScope scope(ScopeLevel level) {
        UserScope s = new UserScope();
        s.setUserId(USER);
        s.setScopeLevel(level);
        return s;
    }

    private void stored(UserScope s) {
        when(userScopeRepository.findById(USER)).thenReturn(Optional.of(s));
    }

    // ---- fail closed ----

    @Test
    void noScopeRow_deniesEverything() {
        // The single most important case: a missing scope must mean "sees
        // nothing", never "sees everything".
        when(userScopeRepository.findById(USER)).thenReturn(Optional.empty());

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.denied()).isTrue();
        assertThat(c.isUnrestricted()).isFalse();
    }

    @Test
    void nullOrBlankUser_denies() {
        assertThat(service.scopeFor(null).denied()).isTrue();
        assertThat(service.scopeFor("  ").denied()).isTrue();
    }

    @Test
    void stateScopeWithNoStateId_denies() {
        // A malformed row must not degrade to unrestricted. The DB CHECK should
        // prevent this, but the service does not rely on that.
        stored(scope(ScopeLevel.STATE));

        assertThat(service.scopeFor(USER).denied()).isTrue();
    }

    @Test
    void zoneScopeOverAnEmptyZone_denies() {
        // A ZONE scope whose zone has no LGAs (or no zone set) sees nothing,
        // rather than degrading to an unrestricted "lga IN ()".
        UserScope s = scope(ScopeLevel.ZONE);
        s.setZoneId("z-empty");
        stored(s);
        assertThat(service.scopeFor(USER).denied()).isTrue();
    }

    @Test
    void specialistScopeWithNoUserId_denies() {
        stored(scope(ScopeLevel.SPECIALIST));
        assertThat(service.scopeFor(USER).denied()).isTrue();
    }

    @Test
    void regionalScopeOverAnEmptyRegion_denies() {
        // Otherwise "state_id IN ()" would match nothing or, worse, be dropped.
        UserScope s = scope(ScopeLevel.REGIONAL);
        s.setRegionId("southwest");
        stored(s);
        when(userScopeRepository.findStateIdsByRegionId("southwest")).thenReturn(List.of());

        assertThat(service.scopeFor(USER).denied()).isTrue();
    }

    // ---- levels ----

    @Test
    void nationalIsUnrestricted() {
        stored(scope(ScopeLevel.NATIONAL));

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.isUnrestricted()).isTrue();
        assertThat(c.denied()).isFalse();
    }

    @Test
    void stateResolvesToASingleStateId() {
        UserScope s = scope(ScopeLevel.STATE);
        s.setStateId(25);
        stored(s);

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.STATE);
        assertThat(c.stateIds()).containsExactly(25);
        assertThat(c.isUnrestricted()).isFalse();
    }

    @Test
    void regionalFlattensToItsMemberStates() {
        // REGIONAL and STATE both reduce to "state in set", so the region is
        // expanded once here rather than joined on every scoped query.
        UserScope s = scope(ScopeLevel.REGIONAL);
        s.setRegionId("southwest");
        stored(s);
        when(userScopeRepository.findStateIdsByRegionId("southwest"))
                .thenReturn(List.of(13, 25, 28, 29, 30, 31));

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.REGIONAL);
        assertThat(c.stateIds()).containsExactlyInAnyOrder(13, 25, 28, 29, 30, 31);
        assertThat(c.isUnrestricted()).isFalse();
    }

    @Test
    void zoneResolvesToItsLgaSet() {
        // A zone is one or more LGAs; scope filters to any of them.
        UserScope s = scope(ScopeLevel.ZONE);
        s.setZoneId("z-ikorodu-area");
        stored(s);
        when(zoneRepository.findLgaIds("z-ikorodu-area")).thenReturn(List.of(377, 401, 402));

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.ZONE);
        assertThat(c.lgaIds()).containsExactlyInAnyOrder(377, 401, 402);
        assertThat(c.stateIds()).isEmpty();
    }

    @Test
    void specialistResolvesToTheirOwnUserId() {
        UserScope s = scope(ScopeLevel.SPECIALIST);
        s.setSpecialistUserId("specialist-9");
        stored(s);

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.SPECIALIST);
        assertThat(c.specialistUserId()).isEqualTo("specialist-9");
    }

    // ---- unmapped entities ----

    @Test
    void unmappedEntity_throwsRatherThanReturningUnrestricted() {
        // Payment has no scope rule yet. Defaulting to "no restriction" would
        // turn a missing rule into a data leak, so it must fail loudly.
        ScopeContext lagos = ScopeContext.states(ScopeLevel.STATE, java.util.Set.of(25));

        assertThatThrownBy(() -> service.specificationFor(Payment.class, lagos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No scope rule defined for Payment");
    }

    @Test
    void deniedScope_shortCircuitsBeforeTheEntityLookup() {
        // A user who can see nothing needs no per-entity rule, so this must not
        // throw even for an unmapped entity.
        assertThat(service.specificationFor(Payment.class, ScopeContext.denyAll())).isNotNull();
    }

    @Test
    void nationalScope_shortCircuitsBeforeTheEntityLookup() {
        // Unrestricted is entity-independent too.
        assertThat(service.specificationFor(Payment.class, ScopeContext.national())).isNotNull();
    }

    @Test
    void mappedEntities_returnASpecification() {
        ScopeContext lagos = ScopeContext.states(ScopeLevel.STATE, java.util.Set.of(25));
        assertThat(service.specificationFor(Booking.class, lagos)).isNotNull();
        assertThat(service.specificationFor(
                com.work.mautonlaundry.data.model.Address.class, lagos)).isNotNull();
    }

    @Test
    void getScope_withNoAuthenticatedUser_denies() {
        // SecurityUtil has no context here, so this is the unauthenticated path.
        assertThat(service.currentScope().denied()).isTrue();
        assertThat(service.getScope(Booking.class)).isNotNull();
    }

    // ---- cache ----

    @Test
    void cacheMiss_resolvesAndWritesWithFiveMinuteTtl() {
        UserScope s = scope(ScopeLevel.STATE);
        s.setStateId(25);
        stored(s);

        service.scopeFor(USER);

        verify(valueOperations).set(eq("scope:v1:" + USER), anyString(), eq(Duration.ofMinutes(5)));
        assertThat(ScopeFilterService.CACHE_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void cacheHit_doesNotTouchDatabase() throws Exception {
        when(valueOperations.get("scope:v1:" + USER)).thenReturn(
                "{\"level\":\"STATE\",\"stateIds\":[25],\"lgaIds\":[],"
                        + "\"specialistUserId\":null,\"denied\":false}");

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.stateIds()).containsExactly(25);
        verify(userScopeRepository, never()).findById(anyString());
    }

    @Test
    void corruptCacheEntry_fallsBackToDatabaseRatherThanDenying() {
        when(valueOperations.get(anyString())).thenReturn("not json");
        UserScope s = scope(ScopeLevel.STATE);
        s.setStateId(25);
        stored(s);

        assertThat(service.scopeFor(USER).stateIds()).containsExactly(25);
    }

    @Test
    void redisDown_fallsBackToDatabase() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));
        UserScope s = scope(ScopeLevel.ZONE);
        s.setZoneId("z1");
        stored(s);
        when(zoneRepository.findLgaIds("z1")).thenReturn(List.of(377));

        assertThat(service.scopeFor(USER).lgaIds()).containsExactly(377);
    }

    @Test
    void invalidate_deletesTheUsersKey() {
        service.invalidate(USER);
        verify(redisTemplate).delete("scope:v1:" + USER);
    }

    @Test
    void invalidateFailure_isSwallowed() {
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("down"));
        service.invalidate(USER);  // must not propagate
    }

    @Test
    void invalidateNullUser_isNoOp() {
        service.invalidate(null);
        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- temporary scope upgrades (spec §6) ----

    @Test
    void aLiveTemporaryUpgradeWinsOverThePermanentScope() {
        // The reason scope is read from the database rather than the JWT.
        UserScope permanent = scope(ScopeLevel.ZONE);
        permanent.setZoneId("z1");
        stored(permanent);

        TemporaryScopeGrant upgrade = new TemporaryScopeGrant();
        upgrade.setTemporaryScopeLevel(ScopeLevel.STATE);
        upgrade.setTemporaryStateId(25);
        when(temporaryScopeGrantRepository.findActiveFor(eq(USER), any())).thenReturn(Optional.of(upgrade));

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.STATE);
        assertThat(c.stateIds()).containsExactly(25);
        assertThat(c.lgaIds()).isEmpty();   // the zone scope is superseded, not merged
    }

    @Test
    void anExpiredUpgradeIsNotApplied_evenBeforeTheRevertJobRuns() {
        // findActiveFor filters on expires_at, so a lapse takes effect on the
        // next request rather than whenever the sweep next runs. Relying on the
        // job alone would leave a window where expired scope still worked.
        UserScope permanent = scope(ScopeLevel.ZONE);
        permanent.setZoneId("z1");
        stored(permanent);
        when(zoneRepository.findLgaIds("z1")).thenReturn(List.of(377));
        when(temporaryScopeGrantRepository.findActiveFor(eq(USER), any())).thenReturn(Optional.empty());

        ScopeContext c = service.scopeFor(USER);

        assertThat(c.level()).isEqualTo(ScopeLevel.ZONE);
        assertThat(c.lgaIds()).containsExactly(377);
    }

    @Test
    void aTemporaryUpgradeCanReachNational() {
        stored(scope(ScopeLevel.ZONE));
        TemporaryScopeGrant upgrade = new TemporaryScopeGrant();
        upgrade.setTemporaryScopeLevel(ScopeLevel.NATIONAL);
        when(temporaryScopeGrantRepository.findActiveFor(eq(USER), any())).thenReturn(Optional.of(upgrade));

        assertThat(service.scopeFor(USER).isUnrestricted()).isTrue();
    }

    @Test
    void aTemporaryUpgradeAppliesEvenWithNoPermanentScopeRow() {
        // Fail-closed is about the absence of any scope, not the absence of a
        // permanent one.
        when(userScopeRepository.findById(USER)).thenReturn(Optional.empty());
        TemporaryScopeGrant upgrade = new TemporaryScopeGrant();
        upgrade.setTemporaryScopeLevel(ScopeLevel.STATE);
        upgrade.setTemporaryStateId(25);
        when(temporaryScopeGrantRepository.findActiveFor(eq(USER), any())).thenReturn(Optional.of(upgrade));

        assertThat(service.scopeFor(USER).stateIds()).containsExactly(25);
    }
}

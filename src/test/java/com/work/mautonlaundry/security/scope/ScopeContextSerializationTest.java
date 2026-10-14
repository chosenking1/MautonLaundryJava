package com.work.mautonlaundry.security.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScopeFilterService caches a ScopeContext in Redis as JSON and reads it back
 * with the same ObjectMapper. Nothing previously checked that round trip, so a
 * derived getter (isUnrestricted) was serialised as a property the record's
 * constructor could not accept: every read threw, every request silently fell
 * back to the database, and the cache never worked at all while the log filled
 * with warnings.
 *
 * <p>These run the exact write-then-read the service performs.
 */
class ScopeContextSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ScopeContext roundTrip(ScopeContext original) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(original), ScopeContext.class);
    }

    @Test
    void nationalScopeSurvivesTheCache() throws Exception {
        ScopeContext restored = roundTrip(ScopeContext.national());
        assertThat(restored.isUnrestricted()).isTrue();
        assertThat(restored.level()).isEqualTo(ScopeLevel.NATIONAL);
    }

    @Test
    void stateScopeSurvivesTheCache() throws Exception {
        ScopeContext restored = roundTrip(ScopeContext.states(ScopeLevel.STATE, Set.of(7, 12)));
        assertThat(restored.stateIds()).containsExactlyInAnyOrder(7, 12);
        assertThat(restored.isUnrestricted()).isFalse();
    }

    @Test
    void zoneScopeSurvivesTheCache() throws Exception {
        ScopeContext restored = roundTrip(ScopeContext.zone(Set.of(101, 102, 103)));
        assertThat(restored.lgaIds()).containsExactlyInAnyOrder(101, 102, 103);
    }

    @Test
    void specialistScopeSurvivesTheCache() throws Exception {
        ScopeContext restored = roundTrip(ScopeContext.specialist("user-1"));
        assertThat(restored.specialistUserId()).isEqualTo("user-1");
        assertThat(restored.isUnrestricted()).isFalse();
    }

    @Test
    void denyAllSurvivesTheCache() throws Exception {
        // The most important one to preserve: a deny that decoded wrongly would
        // widen access rather than narrow it.
        ScopeContext restored = roundTrip(ScopeContext.denyAll());
        assertThat(restored.denied()).isTrue();
        assertThat(restored.isUnrestricted()).isFalse();
    }

    @Test
    void derivedPropertyIsNotWrittenToTheCache() throws Exception {
        // Guards the specific regression: a derived getter must not appear in
        // the JSON, because the canonical constructor cannot take it back.
        assertThat(mapper.writeValueAsString(ScopeContext.national()))
                .doesNotContain("unrestricted");
    }
}

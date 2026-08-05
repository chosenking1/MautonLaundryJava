package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rider is shown a first name, not a full identity: enough to greet the
 * right person at the gate, no more.
 */
class DeliveryContactNameTest {

    private AppUser named(String fullName) {
        AppUser u = new AppUser();
        u.setFull_name(fullName);
        return u;
    }

    @Test
    void takesTheFirstNameOnly() {
        assertThat(DeliveryService.firstNameOf(named("Adaeze Okonkwo"))).isEqualTo("Adaeze");
        assertThat(DeliveryService.firstNameOf(named("Chidi Ada Nwosu"))).isEqualTo("Chidi");
    }

    @Test
    void handlesASingleName() {
        assertThat(DeliveryService.firstNameOf(named("Simbiat"))).isEqualTo("Simbiat");
    }

    @Test
    void handlesUntidyInput() {
        assertThat(DeliveryService.firstNameOf(named("  Tunde  Bakare "))).isEqualTo("Tunde");
    }

    @Test
    void missingNamesYieldNullRatherThanEmptyText() {
        // A blank contact name on the rider's screen is worse than none: it
        // looks like a loading bug rather than absent data.
        assertThat(DeliveryService.firstNameOf(null)).isNull();
        assertThat(DeliveryService.firstNameOf(named(null))).isNull();
        assertThat(DeliveryService.firstNameOf(named("   "))).isNull();
    }
}

package com.work.mautonlaundry.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customer-facing text was fixed and the agent-facing text was not, so a
 * rider's offer email read "New PICKUP_FROM_CUSTOMER assignment offer for
 * booking 0af6dfbd-e30c-4349-887b-14f54c4912de". These pin that no internal
 * value reaches a rider or a laundry partner.
 */
class AgentNotificationTextTest {

    @Test
    void everyPhaseReadsAsEnglish() {
        assertThat(AgentNotificationText.phaseLabel("PICKUP_FROM_CUSTOMER"))
                .isEqualTo("collection from a customer");
        assertThat(AgentNotificationText.phaseLabel("RETURN_TO_CUSTOMER"))
                .isEqualTo("return to a customer");
    }

    @Test
    void anUnknownPhaseSaysSomethingTrueRatherThanLeakingIt() {
        for (String unknown : new String[] {null, "", "SOME_NEW_PHASE"}) {
            String label = AgentNotificationText.phaseLabel(unknown);
            assertThat(label).isEqualTo("delivery job");
            assertThat(label).doesNotContain("_");
        }
    }

    @Test
    void theOfferNamesTheOrderAndTheDistance() {
        String offer = AgentNotificationText.deliveryOffer(
                "PICKUP_FROM_CUSTOMER", "TRK1754429181234", 5.0);
        assertThat(offer).contains("collection from a customer");
        assertThat(offer).contains("TRK1754429181234");
        assertThat(offer).contains("5.0 km");
    }

    @Test
    void noEnumNameSurvivesIntoAnAgentMessage() {
        String offer = AgentNotificationText.deliveryOffer(
                "PICKUP_FROM_CUSTOMER", "TRK1", 2.0);
        assertThat(offer).doesNotContain("PICKUP_FROM_CUSTOMER");
        assertThat(offer).doesNotContain("_");
    }

    @Test
    void distanceIsRoundedToWhatAStraightLineCanActuallyClaim() {
        // 5.00 km implies a precision straight-line distance does not have.
        assertThat(AgentNotificationText.deliveryOffer("RETURN_TO_CUSTOMER", "TRK1", 5.0))
                .contains("5.0 km")
                .doesNotContain("5.00 km");
    }

    @Test
    void anUnresolvedReferenceDegradesRatherThanShowingAUuid() {
        for (String missing : new String[] {null, "", "  "}) {
            String offer = AgentNotificationText.deliveryOffer("RETURN_TO_CUSTOMER", missing, 3.0);
            assertThat(offer).contains("return to a customer");
            assertThat(offer).doesNotContain("null");
            assertThat(offer).doesNotContain("order )");

            String laundry = AgentNotificationText.laundryOffer(missing);
            assertThat(laundry).isEqualTo(
                    "A new order is waiting for a laundry partner. Open the app to accept or pass.");
        }
    }

    @Test
    void theLaundryOfferNamesTheOrder() {
        assertThat(AgentNotificationText.laundryOffer("TRK1754429181234"))
                .startsWith("Order TRK1754429181234 is waiting");
    }

    @Test
    void bothOffersTellTheAgentWhatToDo() {
        // An offer with no action is just noise in an inbox.
        assertThat(AgentNotificationText.deliveryOffer("PICKUP_FROM_CUSTOMER", "TRK1", 1.0))
                .contains("Open the app");
        assertThat(AgentNotificationText.laundryOffer("TRK1")).contains("Open the app");
    }
}

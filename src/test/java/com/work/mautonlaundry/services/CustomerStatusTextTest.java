package com.work.mautonlaundry.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Customers were emailed "your laundry is in DELIVERED_TO_LAUNDRY". These pin the
 * rule that no internal vocabulary ever reaches them.
 */
class CustomerStatusTextTest {

    @Test
    void theStatusThatLeakedNowReadsAsEnglish() {
        String text = CustomerStatusText.forStatus("DELIVERED_TO_LAUNDRY");
        assertThat(text).isEqualTo("Your laundry has arrived at our facility.");
        assertThat(text).doesNotContain("_");
    }

    @Test
    void noStatusEverExposesItsRawName() {
        String[] statuses = {
            "CREATED", "LAUNDRY_ASSIGNMENT_PENDING", "LAUNDRY_ACCEPTED",
            "PICKUP_DISPATCH_PENDING", "PICKUP_AGENT_ASSIGNED", "PICKED_UP",
            "AT_LAUNDRY", "WASHING", "READY_FOR_DELIVERY",
            "DELIVERY_DISPATCH_PENDING", "DELIVERY_AGENT_ASSIGNED",
            "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED", "CANCELLED",
            "ENROUTE_TO_CUSTOMER", "ARRIVED_AT_CUSTOMER", "PICKED_UP_FROM_CUSTOMER",
            "ENROUTE_TO_LAUNDRY", "ARRIVED_AT_LAUNDRY", "DELIVERED_TO_LAUNDRY",
            "PICKED_UP_FROM_LAUNDRY", "ENROUTE_FROM_LAUNDRY_TO_CUSTOMER",
            "ARRIVED_AT_CUSTOMER_FOR_DELIVERY", "DELIVERED_TO_CUSTOMER",
        };
        for (String status : statuses) {
            String text = CustomerStatusText.forStatus(status);
            assertThat(text).as("text for %s", status).doesNotContain("_");
            assertThat(text).as("text for %s", status).doesNotContain(status);
            assertThat(text).as("text for %s", status).endsWith(".");
        }
    }

    @Test
    void anUnknownStatusSaysSomethingCalmRatherThanLeaking() {
        // A status added after this ships is a deployment detail, not news.
        String text = CustomerStatusText.forStatus("SOME_FUTURE_STATE");
        assertThat(text).isEqualTo("Your order has been updated.");
        assertThat(text).doesNotContain("SOME_FUTURE_STATE");
    }

    @Test
    void nullAndBlankAreHandled() {
        assertThat(CustomerStatusText.forStatus(null)).isEqualTo("Your order has been updated.");
        assertThat(CustomerStatusText.forStatus("  ")).isEqualTo("Your order has been updated.");
    }

    @Test
    void headlinesAreShortPhrasesNotStatusNames() {
        assertThat(CustomerStatusText.headlineFor("DELIVERED_TO_LAUNDRY")).isEqualTo("Cleaning in progress");
        assertThat(CustomerStatusText.headlineFor("OUT_FOR_DELIVERY")).isEqualTo("Out for delivery");
        assertThat(CustomerStatusText.headlineFor("WHAT_IS_THIS")).isEqualTo("Order update");
    }
}

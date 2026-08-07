package com.work.mautonlaundry.services;

/**
 * Turns internal values into sentences an agent can read.
 *
 * <p>The customer-facing text was fixed and the agent-facing text was not, so a
 * rider's offer email still read "New PICKUP_FROM_CUSTOMER assignment offer for
 * booking 0af6dfbd-e30c-4349-887b-14f54c4912de". Riders and laundry partners are
 * not colleagues with access to the enum; they are people deciding whether to
 * take a job in the next thirty seconds.
 *
 * <p>Separate from {@link CustomerStatusText} because the audiences need
 * different things. A customer is told what happened to their laundry; an agent
 * is told what work is on offer and what to do about it.
 */
public final class AgentNotificationText {

    private AgentNotificationText() {
    }

    /** What kind of run this is, in the words an agent would use. */
    public static String phaseLabel(String phase) {
        if (phase == null || phase.isBlank()) {
            return "delivery job";
        }
        return switch (phase.trim().toUpperCase()) {
            case "PICKUP_FROM_CUSTOMER" -> "collection from a customer";
            case "RETURN_TO_CUSTOMER" -> "return to a customer";
            // An unrecognised phase is our problem, not something to hand over.
            default -> "delivery job";
        };
    }

    /**
     * The offer a rider receives.
     *
     * @param reference the order's tracking number, or null if it could not be
     *                  resolved -- in which case the sentence simply omits it
     *                  rather than falling back to an internal id
     */
    public static String deliveryOffer(String phase, String reference, double distanceKm) {
        String order = reference == null || reference.isBlank() ? "" : " (order " + reference + ")";
        // One decimal: a rider decides on "about 5 km", and 5.00 km implies a
        // precision that straight-line distance does not have.
        return "New " + phaseLabel(phase) + order
                + String.format(", about %.1f km away.", distanceKm)
                + " Open the app to accept or pass.";
    }

    /** The offer a laundry partner receives. */
    public static String laundryOffer(String reference) {
        String order = reference == null || reference.isBlank() ? "A new order" : "Order " + reference;
        return order + " is waiting for a laundry partner. Open the app to accept or pass.";
    }
}

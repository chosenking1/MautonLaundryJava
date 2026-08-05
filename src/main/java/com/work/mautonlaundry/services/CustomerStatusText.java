package com.work.mautonlaundry.services;

/**
 * Turns internal statuses into sentences a customer can read.
 *
 * <p>Status names leaked straight into emails -- "your laundry is in
 * DELIVERED_TO_LAUNDRY" -- because the only formatter covered booking statuses
 * and fell back to replacing underscores for everything else. Delivery
 * assignment statuses went through that fallback, so the customer got the
 * internal vocabulary of the dispatch system.
 *
 * <p>These say what happened rather than naming a state. "At Laundry" is a label
 * for us; "Your laundry has arrived at our facility" is information for them. The
 * unknown case says something true and calm rather than exposing the raw value,
 * because a status this file has not seen is a deployment detail, not news the
 * customer can act on.
 */
public final class CustomerStatusText {

    private CustomerStatusText() {
    }

    /** What to tell the customer has just happened. */
    public static String forStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Your order has been updated.";
        }
        return switch (status.trim().toUpperCase()) {
            // ---- booking lifecycle ----
            case "CREATED" ->
                    "We have your order and we're finding a laundry partner nearby.";
            case "LAUNDRY_ASSIGNMENT_PENDING" ->
                    "We're matching your order with a laundry partner.";
            case "LAUNDRY_ACCEPTED" ->
                    "A laundry partner has accepted your order.";
            case "PICKUP_DISPATCH_PENDING" ->
                    "We're finding a rider to collect your laundry.";
            case "PICKUP_AGENT_ASSIGNED" ->
                    "A rider has been assigned and will collect your laundry soon.";
            case "PICKED_UP", "PICKED_UP_FROM_CUSTOMER" ->
                    "Your laundry has been collected. It's on its way to be cleaned.";
            case "AT_LAUNDRY", "DELIVERED_TO_LAUNDRY" ->
                    "Your laundry has arrived at our facility.";
            case "WASHING" ->
                    "Your laundry is being cleaned.";
            case "READY_FOR_DELIVERY" ->
                    "Your laundry is clean and ready to come back to you.";
            case "DELIVERY_DISPATCH_PENDING" ->
                    "We're finding a rider to bring your laundry back.";
            case "DELIVERY_AGENT_ASSIGNED" ->
                    "A rider has been assigned to bring your laundry back.";
            case "PICKED_UP_FROM_LAUNDRY", "ENROUTE_FROM_LAUNDRY_TO_CUSTOMER", "OUT_FOR_DELIVERY" ->
                    "Your clean laundry is on its way to you.";
            case "ARRIVED_AT_CUSTOMER_FOR_DELIVERY" ->
                    "Your rider has arrived with your laundry.";
            case "DELIVERED", "DELIVERED_TO_CUSTOMER" ->
                    "Your laundry has been delivered. Thank you for choosing us.";
            case "COMPLETED" ->
                    "Your order is complete. Thank you for choosing us.";
            case "CANCELLED" ->
                    "Your order has been cancelled.";

            // ---- rider movement, when it reaches the customer ----
            case "ENROUTE_TO_CUSTOMER" ->
                    "Your rider is on the way to you.";
            case "ARRIVED_AT_CUSTOMER" ->
                    "Your rider has arrived to collect your laundry.";
            case "ENROUTE_TO_LAUNDRY" ->
                    "Your laundry is on its way to our facility.";
            case "ARRIVED_AT_LAUNDRY" ->
                    "Your rider has reached our facility.";

            // An unrecognised status is our problem, not something to hand over.
            default -> "Your order has been updated.";
        };
    }

    /**
     * What to tell a customer whose booking is waiting for a window they chose.
     *
     * <p>Such a booking sits in CREATED, and CREATED says we are finding a
     * laundry partner nearby -- true for a booking placed for now, a lie for one
     * held until Thursday. Someone who is told we are looking, and then hears
     * nothing for two days, reasonably concludes we lost it.
     *
     * @param window a phrase like "Thursday, Morning 8:00 - 12:00"
     */
    public static String forScheduledPickup(String window) {
        if (window == null || window.isBlank()) {
            return "Your pickup is booked. We'll be in touch before we set off.";
        }
        return "Your pickup is booked for " + window
                + ". We'll arrange a rider closer to the time.";
    }

    /**
     * A short heading for the same event, for an email subject or a push title.
     * Still a phrase, never a status name.
     */
    public static String headlineFor(String status) {
        if (status == null || status.isBlank()) {
            return "Order update";
        }
        return switch (status.trim().toUpperCase()) {
            case "CREATED", "LAUNDRY_ASSIGNMENT_PENDING" -> "Order received";
            case "LAUNDRY_ACCEPTED" -> "Laundry partner confirmed";
            case "PICKUP_DISPATCH_PENDING", "PICKUP_AGENT_ASSIGNED" -> "Collection on the way";
            case "PICKED_UP", "PICKED_UP_FROM_CUSTOMER" -> "Laundry collected";
            case "AT_LAUNDRY", "DELIVERED_TO_LAUNDRY", "WASHING" -> "Cleaning in progress";
            case "READY_FOR_DELIVERY" -> "Ready to come back";
            case "DELIVERY_DISPATCH_PENDING", "DELIVERY_AGENT_ASSIGNED",
                 "PICKED_UP_FROM_LAUNDRY", "OUT_FOR_DELIVERY" -> "Out for delivery";
            case "DELIVERED", "DELIVERED_TO_CUSTOMER", "COMPLETED" -> "Delivered";
            case "CANCELLED" -> "Order cancelled";
            default -> "Order update";
        };
    }
}

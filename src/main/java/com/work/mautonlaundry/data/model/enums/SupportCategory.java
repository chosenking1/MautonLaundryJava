package com.work.mautonlaundry.data.model.enums;

/**
 * What a complaint is about. Chosen by the customer at submission so tickets can
 * be routed and counted without someone reading every one first.
 *
 * <p>Deliberately short: a long list makes people pick wrong, and OTHER plus a
 * written subject carries anything the list misses.
 */
public enum SupportCategory {
    /** Wrong items, missing items, order not collected or delivered. */
    ORDER_ISSUE,
    /** Something came back damaged, stained or did not come back at all. */
    DAMAGE_OR_LOSS,
    /** Charged wrongly, payment failed, refund not received. */
    PAYMENT,
    /** Late pickup or delivery, rider conduct, could not be found. */
    DELIVERY,
    /** The app itself misbehaved. */
    APP_PROBLEM,
    OTHER
}

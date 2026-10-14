package com.work.mautonlaundry.data.model.enums;

public enum DeliveryAssignmentStatus {
    OFFERED,
    ACCEPTED,
    DECLINED,
    ENROUTE_TO_CUSTOMER,
    ARRIVED_AT_CUSTOMER,
    PICKED_UP_FROM_CUSTOMER,
    ENROUTE_TO_LAUNDRY,
    ARRIVED_AT_LAUNDRY,
    DELIVERED_TO_LAUNDRY,
    PICKED_UP_FROM_LAUNDRY,
    ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
    ARRIVED_AT_CUSTOMER_FOR_DELIVERY,
    DELIVERED_TO_CUSTOMER,
    COMPLETED,
    CANCELLED;

    /**
     * Statuses where the rider is still on their way and worth contacting.
     *
     * <p>Narrower than {@link #activeAssignmentStatuses()} on purpose. That list
     * exists to stop a job being dispatched twice, so it deliberately includes
     * the finished states -- but showing a customer "call your rider" after the
     * handover is complete is confusing at best and an intrusion on the rider at
     * worst. Once the goods have changed hands there is nothing left to call
     * about.
     */
    public static java.util.List<DeliveryAssignmentStatus> riderEnRouteStatuses() {
        return java.util.List.of(
                ACCEPTED,
                ENROUTE_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER,
                PICKED_UP_FROM_CUSTOMER,
                ENROUTE_TO_LAUNDRY,
                ARRIVED_AT_LAUNDRY,
                PICKED_UP_FROM_LAUNDRY,
                ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER_FOR_DELIVERY
        );
    }

    public static java.util.List<DeliveryAssignmentStatus> activeAssignmentStatuses() {
        return java.util.List.of(
                ACCEPTED,
                ENROUTE_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER,
                PICKED_UP_FROM_CUSTOMER,
                ENROUTE_TO_LAUNDRY,
                ARRIVED_AT_LAUNDRY,
                DELIVERED_TO_LAUNDRY,
                PICKED_UP_FROM_LAUNDRY,
                ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER_FOR_DELIVERY,
                DELIVERED_TO_CUSTOMER,
                COMPLETED
        );
    }

    public static java.util.List<DeliveryAssignmentStatus> inProgressStatuses() {
        return java.util.List.of(
                ACCEPTED,
                ENROUTE_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER,
                PICKED_UP_FROM_CUSTOMER,
                ENROUTE_TO_LAUNDRY,
                ARRIVED_AT_LAUNDRY,
                PICKED_UP_FROM_LAUNDRY,
                ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
                ARRIVED_AT_CUSTOMER_FOR_DELIVERY
        );
    }
}

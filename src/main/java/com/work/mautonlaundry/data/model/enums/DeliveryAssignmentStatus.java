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
                ARRIVED_AT_CUSTOMER_FOR_DELIVERY,
                DELIVERED_TO_CUSTOMER
        );
    }
}

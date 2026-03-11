package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.enums.BookingStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class BookingStateMachine {
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    public void validateTransition(BookingStatus current, BookingStatus next) {
        if (current == next) {
            return;
        }
        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(BookingStatus.class));
        if (!allowed.contains(next)) {
            throw new IllegalArgumentException(
                    "Invalid booking status transition from " + current + " to " + next
            );
        }
    }

    private static Map<BookingStatus, Set<BookingStatus>> buildTransitions() {
        Map<BookingStatus, Set<BookingStatus>> transitions = new EnumMap<>(BookingStatus.class);

        transitions.put(BookingStatus.CREATED, EnumSet.of(BookingStatus.LAUNDRY_ASSIGNMENT_PENDING, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.LAUNDRY_ASSIGNMENT_PENDING, EnumSet.of(BookingStatus.LAUNDRY_ACCEPTED, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.LAUNDRY_ACCEPTED, EnumSet.of(BookingStatus.PICKUP_DISPATCH_PENDING));
        transitions.put(BookingStatus.PICKUP_DISPATCH_PENDING, EnumSet.of(BookingStatus.PICKUP_AGENT_ASSIGNED, BookingStatus.CANCELLED));
        transitions.put(BookingStatus.PICKUP_AGENT_ASSIGNED, EnumSet.of(BookingStatus.PICKED_UP));
        transitions.put(BookingStatus.PICKED_UP, EnumSet.of(BookingStatus.AT_LAUNDRY));
        transitions.put(BookingStatus.AT_LAUNDRY, EnumSet.of(BookingStatus.WASHING));
        transitions.put(BookingStatus.WASHING, EnumSet.of(BookingStatus.READY_FOR_DELIVERY));
        transitions.put(BookingStatus.READY_FOR_DELIVERY, EnumSet.of(BookingStatus.DELIVERY_DISPATCH_PENDING));
        transitions.put(BookingStatus.DELIVERY_DISPATCH_PENDING, EnumSet.of(BookingStatus.DELIVERY_AGENT_ASSIGNED));
        transitions.put(BookingStatus.DELIVERY_AGENT_ASSIGNED, EnumSet.of(BookingStatus.OUT_FOR_DELIVERY));
        transitions.put(BookingStatus.OUT_FOR_DELIVERY, EnumSet.of(BookingStatus.DELIVERED));
        transitions.put(BookingStatus.DELIVERED, EnumSet.of(BookingStatus.COMPLETED));

        transitions.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        transitions.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));

        return transitions;
    }
}

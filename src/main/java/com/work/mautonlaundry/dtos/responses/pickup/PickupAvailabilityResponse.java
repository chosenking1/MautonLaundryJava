package com.work.mautonlaundry.dtos.responses.pickup;

import java.time.LocalDate;
import java.util.List;

/**
 * What the customer may choose from on the Schedule step.
 *
 * <p>{@code asapSelectable} is the existing behaviour kept as a first-class
 * option rather than an absence: booking with no schedule dispatches
 * immediately, which is what most people want and what every already-released
 * app version does.
 */
public record PickupAvailabilityResponse(
        boolean asapSelectable,
        String asapLabel,
        List<Day> days
) {
    public record Day(
            LocalDate date,
            /** "Today", "Tomorrow", then "Thu 7 Aug" -- a date alone makes people count. */
            String label,
            boolean hasSelectableSlot,
            List<PickupSlotOption> slots
    ) {}
}

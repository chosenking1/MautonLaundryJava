package com.work.mautonlaundry.dtos.responses.pickup;

import java.time.LocalTime;

/**
 * One window on one day, and whether it can still be chosen.
 *
 * <p>Unselectable windows are returned rather than filtered out, with the reason
 * attached. A customer looking at today and seeing only "Evening" cannot tell
 * whether the earlier windows are full, closed, or were never offered -- and
 * "Too soon to arrange" answers that where an absent row does not.
 */
public record PickupSlotOption(
        String slotId,
        String label,
        LocalTime startTime,
        LocalTime endTime,
        boolean selectable,
        String unavailableReason
) {
    public static PickupSlotOption open(String slotId, String label, LocalTime start, LocalTime end) {
        return new PickupSlotOption(slotId, label, start, end, true, null);
    }

    public static PickupSlotOption closed(String slotId, String label, LocalTime start,
                                          LocalTime end, String reason) {
        return new PickupSlotOption(slotId, label, start, end, false, reason);
    }
}

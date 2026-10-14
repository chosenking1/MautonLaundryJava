package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A window of the day a customer can ask to be collected in.
 *
 * <p>Stored rather than hardcoded because the right width is an operations
 * question that changes: coarse blocks while there are few riders, narrower
 * windows once the pool is dense enough to hit them. As config that would be a
 * deploy, and so would never be tuned.
 *
 * <p>Retired slots are deactivated, never deleted -- bookings already placed in
 * a slot still need to say what they were promised.
 */
@Entity
@Table(name = "pickup_slots")
@Getter
@Setter
@NoArgsConstructor
public class PickupSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * What the customer reads. Shown beside the times rather than instead of
     * them: "Evening" on its own is a different four hours to everyone.
     */
    @Column(nullable = false, length = 40, unique = true)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Hand-set, so a slot can be promoted without moving its hours. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** "Morning, 8:00 - 12:00" -- the label and the hours, never one alone. */
    @Transient
    public String describe() {
        return label + ", " + format(startTime) + " - " + format(endTime);
    }

    private static String format(LocalTime t) {
        return t == null ? "" : String.format("%d:%02d", t.getHour(), t.getMinute());
    }
}

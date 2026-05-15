package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One row per (laundry agent user, day-of-week). day_of_week follows Java's
 * DayOfWeek.getValue() convention: 1 = MONDAY ... 7 = SUNDAY. Times are
 * interpreted as Africa/Lagos local time at the service layer.
 *
 * An agent with zero rows is treated as "always open" by
 * LaundryAgentHoursService.isOpenAt — preserves the pre-feature behaviour for
 * existing single-agent setups until hours are explicitly configured.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "laundry_agent_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "day_of_week"}))
public class LaundryAgentHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

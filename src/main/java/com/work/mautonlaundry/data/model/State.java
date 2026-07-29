package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One of Nigeria's 36 states, or the FCT. Reference data seeded by
 * V14__seed_states_lgas_regions.sql -- the application never creates these.
 *
 * <p>Scope filtering keys off this table rather than the old free-text
 * address.state, so that a scope can never point at a state that does not exist
 * (Permission Architecture V2, spec §5).
 */
@Entity
@Table(name = "states")
@Getter
@Setter
@NoArgsConstructor
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Lowercase, alphanumerics only. Produced by GeoNormalizer.normalize() and
     * seeded by the same rule -- the two must never drift, or lookups silently
     * return nothing.
     */
    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

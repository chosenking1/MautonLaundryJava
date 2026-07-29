package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An operational zone: one or more LGAs grouped together within a state
 * (Permission Architecture V2, §5 — the ZONE scope level). Maps V24.
 *
 * <p>A zone can be a single LGA (a standalone zone) or several joined. An LGA
 * belongs to at most one zone. So a small state might have three zones covering
 * all its LGAs, while a dense state like Lagos might have twenty-one, some
 * single-LGA and some grouped.
 *
 * <p>ZONE data scope targets a zone, which resolves to its set of LGAs -- exactly
 * how REGIONAL targets a region that resolves to its set of states, one level
 * down.
 */
@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
public class Zone {

    @Id
    @Column(nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

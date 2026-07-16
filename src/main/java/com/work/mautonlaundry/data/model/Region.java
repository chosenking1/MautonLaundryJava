package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A named group of states (Permission Architecture V2, spec §5.5). Maps V13.
 *
 * <p>Regions are admin-managed, not hardcoded (§5.2): they exist so a REGIONAL
 * scope can resolve to a set of states. Seeded with Nigeria's six geopolitical
 * zones in V14, editable from the portal thereafter.
 */
@Entity
@Table(name = "regions")
@Getter
@Setter
@NoArgsConstructor
public class Region {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "region_name", nullable = false, unique = true)
    private String regionName;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

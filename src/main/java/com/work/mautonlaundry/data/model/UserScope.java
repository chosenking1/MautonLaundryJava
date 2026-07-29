package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A user's permanent data scope (Permission Architecture V2, spec §5.5).
 * Maps V15__user_scope.sql.
 *
 * <p>Exactly one target field is populated, matching scopeLevel, and the
 * database enforces that with a CHECK constraint -- NATIONAL populates none
 * (it is the wildcard). This replaces the spec's single free-text
 * {@code scope_value VARCHAR(200)}: typed foreign keys mean a scope cannot name
 * a state that does not exist, and cannot contradict its own level. Since scope
 * decides who sees whose orders, an invalid one should not be expressible.
 */
@Entity
@Table(name = "user_scope")
@Getter
@Setter
@NoArgsConstructor
public class UserScope {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false)
    private ScopeLevel scopeLevel;

    /** Set only for REGIONAL. */
    @Column(name = "region_id")
    private String regionId;

    /** Set only for STATE. */
    @Column(name = "state_id")
    private Integer stateId;

    /** Set only for ZONE (an operational zone: a group of LGAs). */
    @Column(name = "zone_id")
    private String zoneId;

    /** Set only for SPECIALIST -- the user whose own work is visible. */
    @Column(name = "specialist_user_id")
    private String specialistUserId;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}

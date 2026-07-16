package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A live, time-boxed widening of someone's data scope
 * (Permission Architecture V2, spec §6.3). Maps V22__temporary_scope.sql.
 *
 * <p>Grants no permissions -- only visibility (§6.1). The two dimensions stay
 * independent, so PermissionEvaluationService never reads this.
 */
@Entity
@Table(name = "temporary_scope_grants")
@Getter
@Setter
@NoArgsConstructor
public class TemporaryScopeGrant {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "scope_upgrade_request_id", nullable = false)
    private String scopeUpgradeRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "temporary_scope_level", nullable = false)
    private ScopeLevel temporaryScopeLevel;

    @Column(name = "temporary_region_id")
    private String temporaryRegionId;

    @Column(name = "temporary_state_id")
    private Integer temporaryStateId;

    @Column(name = "temporary_zone_id")
    private String temporaryZoneId;

    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    /**
     * Flipped false by the scheduled revert. Deliberately not derived from
     * expires_at alone: a reverted grant must stay auditable as a thing that
     * existed and ended, and the partial unique index needs a concrete column.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

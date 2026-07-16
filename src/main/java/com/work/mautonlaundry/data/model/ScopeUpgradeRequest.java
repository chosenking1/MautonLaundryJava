package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.model.enums.ScopeUpgradeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A request for temporary wider visibility (spec §6.3). Maps V22. */
@Entity
@Table(name = "scope_upgrade_requests")
@Getter
@Setter
@NoArgsConstructor
public class ScopeUpgradeRequest {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    /**
     * The requester's scope at request time, denormalised. The approver is
     * deciding about a specific delta, and a permanent scope can move while the
     * request sits.
     */
    @Column(name = "current_scope_level", nullable = false)
    private String currentScopeLevel;

    @Column(name = "current_scope_value")
    private String currentScopeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_scope_level", nullable = false)
    private ScopeLevel requestedScopeLevel;

    @Column(name = "requested_region_id")
    private String requestedRegionId;

    @Column(name = "requested_state_id")
    private Integer requestedStateId;

    @Column(name = "requested_lga_id")
    private Integer requestedLgaId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeUpgradeStatus status = ScopeUpgradeStatus.PENDING;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Set at approval. §6.1 allows no permanent upgrade through this path. */
    @Column
    private LocalDateTime expiry;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}

package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A grant whose grantor can no longer make it (spec §2.1). Maps V10.
 *
 * <p>When someone's permissions are reduced, the grants they previously handed
 * out may now exceed what they themselves hold. Those are flagged here rather
 * than revoked: §2.1 is explicit that "they are NOT auto-revoked -- a human must
 * review and confirm or revoke them".
 *
 * <p>That restraint is the point. The recipient may depend on the permission to
 * do their job, and the grantor losing it says nothing about whether the
 * recipient should keep it -- someone changing team is not a reason to silently
 * break everyone they ever onboarded. Auto-revoking would turn one personnel
 * change into an outage.
 */
@Entity
@Table(name = "pending_permission_review")
@Getter
@Setter
@NoArgsConstructor
public class PendingPermissionReview {

    @Id
    @Column(nullable = false)
    private String id;

    /** Who holds the questionable grant. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /** Whose reduction triggered the flag -- the person who made the grant. */
    @Column(name = "original_grantor_id", nullable = false)
    private String originalGrantorId;

    @Column(name = "flagged_at")
    private LocalDateTime flaggedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** KEPT or REVOKED; null while awaiting review. */
    @Column
    private String resolution;
}

package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A record that a user agreed to a specific version of the terms.
 *
 * <p>Rows are only ever inserted, never updated: the point is to be able to show,
 * later, exactly which text someone agreed to and when. Overwriting a previous
 * acceptance when the terms change would destroy the very evidence the record
 * exists to provide.
 */
@Entity
@Table(
        name = "terms_acceptances",
        uniqueConstraints = @UniqueConstraint(
                name = "terms_acceptances_user_version_unique",
                columnNames = {"user_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** The version string the user was shown, e.g. "2026-08-01". */
    @Column(nullable = false, length = 40)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt = LocalDateTime.now();

    /** Where the acceptance came from -- CUSTOMER_APP, OPS_APP, WEB. */
    @Column(length = 40)
    private String source;

    /**
     * The caller's IP at the moment of acceptance. Kept because a consent record
     * with no corroborating detail is weak evidence; it is not used for anything
     * else, and belongs in the privacy policy's list of what we collect.
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
}

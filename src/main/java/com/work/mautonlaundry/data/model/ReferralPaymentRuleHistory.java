package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit of every change to a referrer's payment rules. Captures the
 * full previous and new rule sets as JSON so any historical calculation can be
 * explained.
 */
@Entity
@Table(name = "referral_payment_rule_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralPaymentRuleHistory {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "referrer_id", nullable = false)
    private String referrerId;

    @Column(name = "changed_by")
    private String changedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_rules", columnDefinition = "jsonb")
    private String previousRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_rules", columnDefinition = "jsonb")
    private String newRules;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }
}

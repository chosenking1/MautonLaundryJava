package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ReferralPayoutStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payout owed to a referrer for a period. Generated as PENDING by the
 * scheduled job (or manually), reviewed and APPROVED by an admin, then marked
 * PAID once the bank transfer is made. PAID is terminal and locked.
 */
@Entity
@Table(name = "referral_payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralPayout {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "referrer_id", nullable = false)
    private String referrerId;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_breakdown", columnDefinition = "jsonb")
    private String calculationBreakdown;

    @Column(name = "total_commission_calculated", precision = 12, scale = 2)
    private BigDecimal totalCommissionCalculated;

    @Column(name = "manual_adjustment_amount", precision = 12, scale = 2)
    private BigDecimal manualAdjustmentAmount;

    @Column(name = "manual_adjustment_reason", columnDefinition = "TEXT")
    private String manualAdjustmentReason;

    @Column(name = "final_payout_amount", precision = 12, scale = 2)
    private BigDecimal finalPayoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReferralPayoutStatus status = ReferralPayoutStatus.PENDING;

    @Column(name = "generated_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "payment_reference", columnDefinition = "TEXT")
    private String paymentReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.generatedAt == null) {
            this.generatedAt = LocalDateTime.now();
        }
    }
}

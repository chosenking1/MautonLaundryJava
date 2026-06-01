package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ReferralPaymentFrequency;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single configurable payment rule for a referrer. A referrer may have many
 * active rules simultaneously; the system applies every qualifying rule and
 * sums the results. Which fields are meaningful depends on {@link #ruleType}.
 */
@Entity
@Table(name = "referral_payment_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralPaymentRule {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "referrer_id", nullable = false)
    private String referrerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private ReferralRuleType ruleType;

    @Column(name = "percentage_value", precision = 6, scale = 2)
    private BigDecimal percentageValue;

    @Column(name = "flat_fee_amount", precision = 12, scale = 2)
    private BigDecimal flatFeeAmount;

    @Column(name = "booking_number_from")
    private Integer bookingNumberFrom;

    @Column(name = "booking_number_to")
    private Integer bookingNumberTo;

    @Column(name = "days_from_registration_limit")
    private Integer daysFromRegistrationLimit;

    @Column(name = "volume_threshold")
    private Integer volumeThreshold;

    @Column(name = "milestone_booking_number")
    private Integer milestoneBookingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", nullable = false, length = 20)
    private ReferralPaymentFrequency paymentFrequency;

    /** For PER_MILESTONE frequency: number of repeat customers that triggers a payout. */
    @Column(name = "milestone_payment_threshold")
    private Integer milestonePaymentThreshold;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

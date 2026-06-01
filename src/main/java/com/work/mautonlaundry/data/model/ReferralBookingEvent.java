package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One record per qualifying booking placed by a referred customer. Captures the
 * lifetime booking number, the days since the customer registered, the
 * Imototo commission and gross value of the order, the commission the referrer
 * earned, and an itemised JSON breakdown of which rules were applied and why.
 */
@Entity
@Table(name = "referral_booking_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"booking_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralBookingEvent {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "attribution_id", nullable = false)
    private String attributionId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "booking_number", nullable = false)
    private Integer bookingNumber;

    @Column(name = "days_since_registration", nullable = false)
    private Integer daysSinceRegistration;

    @Column(name = "imototo_commission_on_order", precision = 12, scale = 2)
    private BigDecimal imototoCommissionOnOrder;

    @Column(name = "order_gross_value", precision = 12, scale = 2)
    private BigDecimal orderGrossValue;

    @Column(name = "referrer_commission_earned", precision = 12, scale = 2)
    private BigDecimal referrerCommissionEarned;

    /** JSON array of applied rule line items: ruleId, ruleType, amount, reason. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commission_rules_applied", columnDefinition = "jsonb")
    private String commissionRulesApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

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

package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "discount_usage_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUsageLog {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "discount_id", nullable = false)
    private String discountId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "assignment_id")
    private String assignmentId;

    @Column(name = "discount_code_snapshot", nullable = false, length = 50)
    private String discountCodeSnapshot;

    @Column(name = "discount_type_snapshot", nullable = false, length = 20)
    private String discountTypeSnapshot;

    @Column(name = "discount_value_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValueSnapshot;

    @Column(name = "order_value_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderValueBefore;

    @Column(name = "discount_amount_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmountApplied;

    @Column(name = "order_value_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderValueAfter;

    @Column(name = "applied_at", nullable = false)
    @Builder.Default
    private LocalDateTime appliedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }
}
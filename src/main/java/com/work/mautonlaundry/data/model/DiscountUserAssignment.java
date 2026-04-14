package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "discount_user_assignments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"discount_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUserAssignment {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "discount_id", nullable = false)
    private String discountId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    private String approvedBy;

    private LocalDateTime approvedAt;

    @Column(length = 255)
    private String rejectedReason;

    @Column(nullable = false)
    @Builder.Default
    private Integer usageCountCurrent = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer usageCountLifetime = 0;

    private LocalDateTime lastUsedAt;

    private LocalDateTime lastResetAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
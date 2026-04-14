package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.DiscountType;
import com.work.mautonlaundry.data.model.enums.OwnerType;
import com.work.mautonlaundry.data.model.enums.ResetPeriod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "discounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discount {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OwnerType ownerType = OwnerType.IMOTOTO;

    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal minimumOrderValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal maximumOrderValue;

    @Column(nullable = false)
    @Builder.Default
    private boolean requiresApproval = false;

    @Column(length = 100)
    private String allowedEmailDomain;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxUsesPerUser = 2;

    private Integer maxTotalUses;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentTotalUses = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResetPeriod resetPeriod = ResetPeriod.MONTHLY;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime validFrom = LocalDateTime.now();

    private LocalDateTime validUntil;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private String createdBy;

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
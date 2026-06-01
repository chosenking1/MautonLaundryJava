package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.ReferrerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A referral partner. Crucially, a referrer is NOT a platform user: there is no
 * foreign key to the users table. Specialists, influencers and estate managers
 * are managed entirely from the admin portal and many will never log in. The
 * in-app referral dashboard is only surfaced when a logged-in user's email
 * matches a referrer's email.
 */
@Entity
@Table(name = "referrers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referrer {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "referrer_type", nullable = false, length = 30)
    private ReferrerType referrerType;

    @Column(name = "referral_code", nullable = false, unique = true, length = 50)
    private String referralCode;

    /** Optional link to an existing discount, applied to the referred customer's first order. */
    @Column(name = "linked_discount_id")
    private String linkedDiscountId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

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

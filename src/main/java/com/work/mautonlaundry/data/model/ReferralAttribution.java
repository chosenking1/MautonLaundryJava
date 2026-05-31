package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Links a registered user to the referrer who brought them in. Attribution is
 * set once, at registration only. The unique constraint on user_id enforces
 * "one referrer per user".
 */
@Entity
@Table(name = "referral_attributions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralAttribution {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "referrer_id", nullable = false)
    private String referrerId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "registered_at", nullable = false)
    @Builder.Default
    private LocalDateTime registeredAt = LocalDateTime.now();

    @Column(name = "referral_code_used", length = 50)
    private String referralCodeUsed;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.registeredAt == null) {
            this.registeredAt = LocalDateTime.now();
        }
    }
}

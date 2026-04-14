package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "corporate_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, unique = true, length = 100)
    private String emailDomain;

    @Column(length = 100)
    private String hrContactName;

    @Column(length = 100)
    private String hrContactEmail;

    @Column(length = 100)
    private String invoiceEmail;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String billingCycle = "MONTHLY";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String accountStatus = "ACTIVE";

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime onboardedAt = LocalDateTime.now();

    private UUID onboardedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
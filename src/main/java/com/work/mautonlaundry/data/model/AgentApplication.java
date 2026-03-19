package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import com.work.mautonlaundry.data.model.enums.InspectionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "agent_applications")
@Getter
@Setter
@NoArgsConstructor
public class AgentApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentApplicationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentApplicationStatus status = AgentApplicationStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionStatus inspectionStatus = InspectionStatus.PENDING;

    @Column(name = "inspected_by_admin_id")
    private String inspectedByAdminId;

    @Column(name = "inspection_notes")
    private String inspectionNotes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToMany
    @JoinTable(
            name = "agent_application_addresses",
            joinColumns = @JoinColumn(name = "application_id"),
            inverseJoinColumns = @JoinColumn(name = "address_id")
    )
    private Set<Address> addresses = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

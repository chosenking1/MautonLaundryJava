package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "role_change_requests")
public class RoleChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne
    @JoinColumn(name = "requested_role_id", nullable = false)
    private Role requestedRole;

    @ManyToOne
    @JoinColumn(name = "agent_application_id")
    private AgentApplication agentApplication;

    @Column(name = "requested_by_admin_id", nullable = false)
    private String requestedByAdminId;

    @Column(name = "approved_by_admin_id")
    private String approvedByAdminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

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

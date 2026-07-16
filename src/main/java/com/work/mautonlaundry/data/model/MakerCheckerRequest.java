package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.MakerCheckerStatus;
import com.work.mautonlaundry.data.model.enums.MakerCheckerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A pending assignment awaiting a second pair of eyes
 * (Permission Architecture V2, spec §2.4). Maps V21__maker_checker.sql.
 *
 * <p>Created when a Permission Admin assigns something they do not personally
 * hold. The point is not to block them -- it is to let anyone with
 * PERMISSION_ASSIGN delegate any permission in the system, while ensuring the
 * person who signs it off is someone who can actually evaluate it.
 */
@Entity
@Table(name = "maker_checker_requests")
@Getter
@Setter
@NoArgsConstructor
public class MakerCheckerRequest {

    @Id
    @Column(nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private MakerCheckerType requestType;

    @Column(name = "maker_id", nullable = false)
    private String makerId;

    /** Exactly one of targetUserId / targetRoleId is set (V21 CHECK). */
    @Column(name = "target_user_id")
    private String targetUserId;

    @Column(name = "target_role_id")
    private Long targetRoleId;

    @Column(name = "target_permission_id")
    private Long targetPermissionId;

    @Column(name = "target_module_id")
    private String targetModuleId;

    @Column(name = "target_assign_role_id")
    private Long targetAssignRoleId;

    /** Permission ids withheld when assigning a module (spec §3.3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Long> exclusions;

    /**
     * The permission a checker must personally hold to be eligible (spec §2.3).
     * This is what prevents two equally uninformed admins approving each other.
     */
    @Column(name = "required_checker_permission_id")
    private Long requiredCheckerPermissionId;

    /** Nobody held the required permission at submission time (spec §8.4). */
    @Column(name = "fallback_to_super_admin", nullable = false)
    private Boolean fallbackToSuperAdmin = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MakerCheckerStatus status = MakerCheckerStatus.PENDING;

    @Column(name = "checker_id")
    private String checkerId;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

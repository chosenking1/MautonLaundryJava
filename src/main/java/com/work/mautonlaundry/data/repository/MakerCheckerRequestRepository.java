package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.MakerCheckerRequest;
import com.work.mautonlaundry.data.model.enums.MakerCheckerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MakerCheckerRequestRepository extends JpaRepository<MakerCheckerRequest, String> {

    List<MakerCheckerRequest> findByStatusOrderByCreatedAtAsc(MakerCheckerStatus status);

    List<MakerCheckerRequest> findByMakerIdOrderByCreatedAtDesc(String makerId);

    /**
     * Everyone who personally holds a permission (spec §8.4 getEligibleCheckers).
     *
     * <p>Mirrors PermissionEvaluationService's resolution -- role grants, role
     * modules, direct user modules and explicit user grants, minus exclusions and
     * user-level DENY. It has to: a checker "personally holds" a permission means
     * the same thing here as it does at enforcement, or someone could be notified
     * to approve something they cannot actually do.
     *
     * <p>Native, matching the evaluation query it must agree with.
     */
    @Query(value = """
            SELECT DISTINCT u.id FROM users u
             WHERE u.deleted = false
               AND EXISTS (
                    SELECT 1 FROM user_permissions up
                     WHERE up.user_id = u.id AND up.permission_id = :permissionId
                       AND up.grant_type = 'GRANT'
                    UNION ALL
                    SELECT 1 FROM role_permissions rp
                     WHERE rp.role_id = u.role_id AND rp.permission_id = :permissionId
                       AND rp.grant_type = 'GRANT'
                    UNION ALL
                    SELECT 1 FROM role_module_assignments rma
                      JOIN module_permissions mp ON mp.module_id = rma.module_id
                     WHERE rma.role_id = u.role_id AND mp.permission_id = :permissionId
                       AND NOT EXISTS (SELECT 1 FROM role_module_exclusions rme
                                        WHERE rme.role_module_assignment_id = rma.id
                                          AND rme.permission_id = :permissionId)
                    UNION ALL
                    SELECT 1 FROM user_module_assignments uma
                      JOIN module_permissions mp ON mp.module_id = uma.module_id
                     WHERE uma.user_id = u.id AND mp.permission_id = :permissionId
                       AND NOT EXISTS (SELECT 1 FROM user_module_exclusions ume
                                        WHERE ume.user_module_assignment_id = uma.id
                                          AND ume.permission_id = :permissionId)
               )
               AND NOT EXISTS (
                    SELECT 1 FROM user_permissions d
                     WHERE d.user_id = u.id AND d.permission_id = :permissionId
                       AND d.grant_type = 'DENY'
               )
            """, nativeQuery = true)
    List<String> findEligibleCheckerIds(@Param("permissionId") Long permissionId);

    /** Whoever can approve when no eligible checker exists (spec §2.3 fallback). */
    @Query(value = """
            SELECT DISTINCT u.id FROM users u
              JOIN role_permissions rp ON rp.role_id = u.role_id
              JOIN permissions p ON p.id = rp.permission_id
             WHERE u.deleted = false AND p.name = 'MAKER_CHECKER_FALLBACK'
               AND rp.grant_type = 'GRANT'
            """, nativeQuery = true)
    List<String> findFallbackApproverIds();

    /** Pending and past the escalation window, not yet chased (spec §8.4). */
    @Query("""
            SELECT r FROM MakerCheckerRequest r
             WHERE r.status = com.work.mautonlaundry.data.model.enums.MakerCheckerStatus.PENDING
               AND r.escalatedAt IS NULL
               AND r.createdAt < :threshold
            """)
    List<MakerCheckerRequest> findNeedingEscalation(@Param("threshold") LocalDateTime threshold);

    /** Pending and past its expiry (spec §8.4). */
    @Query("""
            SELECT r FROM MakerCheckerRequest r
             WHERE r.status = com.work.mautonlaundry.data.model.enums.MakerCheckerStatus.PENDING
               AND r.expiresAt < :now
            """)
    List<MakerCheckerRequest> findExpired(@Param("now") LocalDateTime now);
}

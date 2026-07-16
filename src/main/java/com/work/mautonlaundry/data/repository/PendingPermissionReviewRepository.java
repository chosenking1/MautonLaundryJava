package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.PendingPermissionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingPermissionReviewRepository extends JpaRepository<PendingPermissionReview, String> {

    /** The review queue (spec §7.4): flagged and not yet resolved. */
    @Query("SELECT r FROM PendingPermissionReview r WHERE r.reviewedAt IS NULL ORDER BY r.flaggedAt ASC")
    List<PendingPermissionReview> findUnreviewed();

    /**
     * Live GRANT rows this person handed out, as (recipientId, permissionId).
     *
     * <p>Only GRANTs: a DENY they issued is not something they needed to hold in
     * order to issue, so losing a permission never makes their past DENY suspect.
     */
    @Query(value = """
            SELECT up.user_id, up.permission_id
              FROM user_permissions up
             WHERE up.granted_by = :grantorId AND up.grant_type = 'GRANT'
            """, nativeQuery = true)
    List<Object[]> findGrantsMadeBy(@Param("grantorId") String grantorId);

    /** Already flagged and still open -- so a second reduction does not duplicate it. */
    @Query("""
            SELECT COUNT(r) FROM PendingPermissionReview r
             WHERE r.userId = :userId AND r.permissionId = :permissionId
               AND r.originalGrantorId = :grantorId AND r.reviewedAt IS NULL
            """)
    long countOpen(@Param("userId") String userId, @Param("permissionId") Long permissionId,
                   @Param("grantorId") String grantorId);
}

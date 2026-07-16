package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Individual per-user permission overrides (Permission Architecture V2, §2.7).
 *
 * <p>Bound to Permission rather than a UserPermission entity: the table is a
 * link row with no behaviour, PermissionEvaluationService already reads it
 * natively, and mapping it would add an object graph nothing navigates.
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<Permission, Long> {

    @Query(value = "SELECT grant_type FROM user_permissions WHERE user_id = :userId AND permission_id = :permissionId",
            nativeQuery = true)
    Optional<String> findGrantType(@Param("userId") String userId,
                                   @Param("permissionId") Long permissionId);

    @Modifying
    @Query(value = """
            INSERT INTO user_permissions (id, user_id, permission_id, grant_type, granted_by)
            VALUES (:id, :userId, :permissionId, :grantType, :actorId)
            """, nativeQuery = true)
    void insert(@Param("id") String id, @Param("userId") String userId,
                @Param("permissionId") Long permissionId, @Param("grantType") String grantType,
                @Param("actorId") String actorId);

    @Modifying
    @Query(value = "DELETE FROM user_permissions WHERE user_id = :userId AND permission_id = :permissionId",
            nativeQuery = true)
    void deleteFor(@Param("userId") String userId, @Param("permissionId") Long permissionId);
}

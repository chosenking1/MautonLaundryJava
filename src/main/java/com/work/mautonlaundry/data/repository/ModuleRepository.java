package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Modules and their join tables (Permission Architecture V2, spec §3.5).
 *
 * <p>The join tables are manipulated with native statements rather than mapped
 * as five more entities. They are pure link rows with no behaviour, and
 * PermissionEvaluationService already reads them natively -- mapping them would
 * add object graphs nothing navigates.
 */
@Repository
public interface ModuleRepository extends JpaRepository<Module, String> {

    Optional<Module> findByModuleKey(String moduleKey);

    // ---- bundle contents ----

    @Query(value = "SELECT permission_id FROM module_permissions WHERE module_id = :moduleId",
            nativeQuery = true)
    List<Long> findPermissionIds(@Param("moduleId") String moduleId);

    @Modifying
    @Query(value = """
            INSERT INTO module_permissions (module_id, permission_id, added_by)
            VALUES (:moduleId, :permissionId, :actorId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addPermission(@Param("moduleId") String moduleId,
                       @Param("permissionId") Long permissionId,
                       @Param("actorId") String actorId);

    @Modifying
    @Query(value = "DELETE FROM module_permissions WHERE module_id = :moduleId AND permission_id = :permissionId",
            nativeQuery = true)
    void removePermission(@Param("moduleId") String moduleId,
                          @Param("permissionId") Long permissionId);

    // ---- assignments ----

    @Modifying
    @Query(value = """
            INSERT INTO user_module_assignments (id, user_id, module_id, assigned_by)
            VALUES (:id, :userId, :moduleId, :actorId)
            ON CONFLICT (user_id, module_id) DO NOTHING
            """, nativeQuery = true)
    void assignToUser(@Param("id") String id, @Param("userId") String userId,
                      @Param("moduleId") String moduleId, @Param("actorId") String actorId);

    @Query(value = "SELECT id FROM user_module_assignments WHERE user_id = :userId AND module_id = :moduleId",
            nativeQuery = true)
    Optional<String> findUserAssignmentId(@Param("userId") String userId,
                                          @Param("moduleId") String moduleId);

    @Modifying
    @Query(value = """
            INSERT INTO user_module_exclusions (id, user_module_assignment_id, permission_id, excluded_by)
            VALUES (:id, :assignmentId, :permissionId, :actorId)
            ON CONFLICT (user_module_assignment_id, permission_id) DO NOTHING
            """, nativeQuery = true)
    void excludeForUser(@Param("id") String id, @Param("assignmentId") String assignmentId,
                        @Param("permissionId") Long permissionId, @Param("actorId") String actorId);

    @Modifying
    @Query(value = """
            INSERT INTO role_module_assignments (id, role_id, module_id, assigned_by)
            VALUES (:id, :roleId, :moduleId, :actorId)
            ON CONFLICT (role_id, module_id) DO NOTHING
            """, nativeQuery = true)
    void assignToRole(@Param("id") String id, @Param("roleId") Long roleId,
                      @Param("moduleId") String moduleId, @Param("actorId") String actorId);

    @Query(value = "SELECT id FROM role_module_assignments WHERE role_id = :roleId AND module_id = :moduleId",
            nativeQuery = true)
    Optional<String> findRoleAssignmentId(@Param("roleId") Long roleId,
                                          @Param("moduleId") String moduleId);

    @Modifying
    @Query(value = """
            INSERT INTO role_module_exclusions (id, role_module_assignment_id, permission_id, excluded_by)
            VALUES (:id, :assignmentId, :permissionId, :actorId)
            ON CONFLICT (role_module_assignment_id, permission_id) DO NOTHING
            """, nativeQuery = true)
    void excludeForRole(@Param("id") String id, @Param("assignmentId") String assignmentId,
                        @Param("permissionId") Long permissionId, @Param("actorId") String actorId);

    /**
     * Everyone whose effective permissions this module can change: holders by
     * direct assignment, plus holders via their role.
     *
     * <p>Drives cache invalidation. Because modules resolve at read time, a
     * change to the bundle already reaches every holder -- but their cached set
     * would keep the old answer for up to the TTL, so they must be dropped.
     */
    @Query(value = """
            SELECT uma.user_id FROM user_module_assignments uma WHERE uma.module_id = :moduleId
            UNION
            SELECT u.id FROM users u
              JOIN role_module_assignments rma ON rma.role_id = u.role_id
             WHERE rma.module_id = :moduleId
            """, nativeQuery = true)
    List<String> findHolderUserIds(@Param("moduleId") String moduleId);
}

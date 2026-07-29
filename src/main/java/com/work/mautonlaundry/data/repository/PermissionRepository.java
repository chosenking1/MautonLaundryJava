package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    Optional<Permission> findByEndpointAndMethodAndActiveTrue(String endpoint, String method);

    @Query("SELECT p FROM Permission p JOIN p.roles r WHERE p.endpoint = :endpoint AND p.method = :method AND r.name = :roleName AND p.active = true")
    Optional<Permission> findByEndpointAndMethodAndRoleName(@Param("endpoint") String endpoint,
                                                           @Param("method") String method,
                                                           @Param("roleName") String roleName);

    /**
     * Every permission the user is granted, from all four grant sources
     * (Permission Architecture V2, spec §8.1 steps 2-5), before user-level
     * DENY is subtracted. Native because the module tables have no JPA entities
     * yet -- nothing writes them until ModuleService lands (spec §8.2).
     *
     * <p>The two NOT EXISTS clauses implement "exclusion always wins" (spec
     * §2.1), scoped per assignment: an exclusion is keyed to the specific
     * assignment row, so excluding PAYOUT_VIEW on one module does not block the
     * same permission arriving via a different module or via the role directly.
     *
     * <p>UNION (not UNION ALL) is deliberate -- it de-duplicates a permission
     * that arrives from several sources at once.
     */
    @Query(value = """
            SELECT p.name FROM user_permissions up
              JOIN permissions p ON p.id = up.permission_id
             WHERE up.user_id = :userId AND up.grant_type = 'GRANT' AND p.active = true
            UNION
            SELECT p.name FROM users u
              JOIN role_permissions rp ON rp.role_id = u.role_id
              JOIN permissions p ON p.id = rp.permission_id
             WHERE u.id = :userId AND rp.grant_type = 'GRANT' AND p.active = true
            UNION
            SELECT p.name FROM users u
              JOIN role_module_assignments rma ON rma.role_id = u.role_id
              JOIN module_permissions mp ON mp.module_id = rma.module_id
              JOIN permissions p ON p.id = mp.permission_id
             WHERE u.id = :userId AND p.active = true
               AND NOT EXISTS (SELECT 1 FROM role_module_exclusions rme
                                WHERE rme.role_module_assignment_id = rma.id
                                  AND rme.permission_id = mp.permission_id)
            UNION
            SELECT p.name FROM user_module_assignments uma
              JOIN module_permissions mp ON mp.module_id = uma.module_id
              JOIN permissions p ON p.id = mp.permission_id
             WHERE uma.user_id = :userId AND p.active = true
               AND NOT EXISTS (SELECT 1 FROM user_module_exclusions ume
                                WHERE ume.user_module_assignment_id = uma.id
                                  AND ume.permission_id = mp.permission_id)
            """, nativeQuery = true)
    List<String> findGrantedPermissionNames(@Param("userId") String userId);

    /**
     * Explicit user-level denies (spec §2.6 step 1). Subtracted from the granted
     * set, so a DENY beats every grant source including an explicit user GRANT.
     *
     * <p>Intentionally does not filter on p.active: a DENY must keep denying
     * regardless of the permission row's state.
     */
    @Query(value = """
            SELECT p.name FROM user_permissions up
              JOIN permissions p ON p.id = up.permission_id
             WHERE up.user_id = :userId AND up.grant_type = 'DENY'
            """, nativeQuery = true)
    List<String> findDeniedPermissionNames(@Param("userId") String userId);
}
package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read side of the Permissions Management (§7.1) and Role Builder (§7.3) screens.
 *
 * <p>The writes already exist -- RoleAndPermissionController creates permissions
 * and roles and assigns permissions to roles. What was missing is listing the
 * catalogue, which those screens need before anyone can act on it.
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
public class AccessCatalogController {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    /**
     * Every permission, grouped-friendly with its category (§7.1).
     *
     * <p>§7.1 also wants "which modules include it" and holder counts. Those are
     * deliberately omitted for now rather than shipped half-right: they need
     * aggregate queries across module_permissions and the four grant sources, and
     * a wrong holder count on a screen used to decide deletions is worse than an
     * absent one. The list itself is what the create-permission flow needs.
     */
    @GetMapping("/permissions")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PERMISSION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> permissions() {
        return ResponseEntity.ok(permissionRepository.findAll().stream()
                .sorted(Comparator.comparing((Permission p) -> p.getCategory() == null ? "" : p.getCategory())
                        .thenComparing(Permission::getName))
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "category", p.getCategory() == null ? "UNCATEGORISED" : p.getCategory(),
                        "description", p.getDescription() == null ? "" : p.getDescription()))
                .toList());
    }

    /**
     * Roles with their directly-granted permissions (§7.3).
     *
     * <p>This shows role_permissions, i.e. what is granted directly to the role.
     * A role's full effective set also folds in its assigned modules; the Role
     * Builder starts from the direct grants, and the module contribution is a
     * later addition to the preview rather than a claim made here.
     */
    @GetMapping("/roles")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('ROLE_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> roles() {
        return ResponseEntity.ok(roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "description", r.getDescription() == null ? "" : r.getDescription(),
                        "permissions", r.getPermissions().stream()
                                .map(Permission::getName).sorted().toList()))
                .toList());
    }
}

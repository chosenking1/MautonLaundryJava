package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.dtos.requests.permissionrequests.CreatePermissionRequest;
import com.work.mautonlaundry.dtos.requests.rolerequests.AssignPermissionToRoleRequest;
import com.work.mautonlaundry.dtos.requests.rolerequests.CreateRoleRequest;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleAndPermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    @Transactional
    public Permission createPermission(CreatePermissionRequest request) {
        if (permissionRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Permission with name '" + request.getName() + "' already exists.");
        }
        Permission permission = new Permission();
        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        return permissionRepository.save(permission);
    }

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        if (roleRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Role with name '" + request.getName() + "' already exists.");
        }
        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        return roleRepository.save(role);
    }

    @Transactional
    public Role assignPermissionToRole(AssignPermissionToRoleRequest request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found with ID: " + request.getRoleId()));

        Permission permission = permissionRepository.findById(request.getPermissionId())
                .orElseThrow(() -> new RuntimeException("Permission not found with ID: " + request.getPermissionId()));

        role.getPermissions().add(permission);
        Role saved = roleRepository.save(role);
        invalidateCachedPermissions();
        return saved;
    }

    /**
     * Takes a permission off a role. The counterpart to
     * {@link #assignPermissionToRole} -- without it a permission granted to a
     * role by mistake could never be withdrawn from the portal.
     *
     * <p>Removing a permission a role does not hold is a no-op rather than an
     * error, so the operation is safely repeatable.
     */
    @Transactional
    public Role removePermissionFromRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found with ID: " + roleId));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Permission not found with ID: " + permissionId));

        role.getPermissions().remove(permission);
        Role saved = roleRepository.save(role);
        invalidateCachedPermissions();
        return saved;
    }

    /**
     * A role change alters the effective permissions of every user holding that
     * role, and the evaluation cache is keyed by user, not by role -- so there is
     * no narrower invalidation available. Skipping this would leave a revoked
     * permission usable for the rest of the cache TTL, which for a revoke is a
     * security hole rather than a staleness annoyance.
     */
    private void invalidateCachedPermissions() {
        permissionEvaluationService.invalidateAll();
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.dtos.requests.permissionrequests.CreatePermissionRequest;
import com.work.mautonlaundry.dtos.requests.rolerequests.AssignPermissionToRoleRequest;
import com.work.mautonlaundry.dtos.requests.rolerequests.CreateRoleRequest;
import com.work.mautonlaundry.services.RoleAndPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/access-control")
@RequiredArgsConstructor
public class RoleAndPermissionController {

    private final RoleAndPermissionService roleAndPermissionService;

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public ResponseEntity<Permission> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        Permission permission = roleAndPermissionService.createPermission(request);
        return new ResponseEntity<>(permission, HttpStatus.CREATED);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public ResponseEntity<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleAndPermissionService.createRole(request);
        return new ResponseEntity<>(role, HttpStatus.CREATED);
    }

    @PostMapping("/roles/assign-permission")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_ASSIGN')")
    public ResponseEntity<Role> assignPermissionToRole(@Valid @RequestBody AssignPermissionToRoleRequest request) {
        Role updatedRole = roleAndPermissionService.assignPermissionToRole(request);
        return ResponseEntity.ok(updatedRole);
    }
}

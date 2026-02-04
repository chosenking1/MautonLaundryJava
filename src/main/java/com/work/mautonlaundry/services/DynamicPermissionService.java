package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DynamicPermissionService {
    
    private final PermissionRepository permissionRepository;

    public boolean hasPermission(String endpoint, String method) {
        // Check if endpoint exists in permission table
        Optional<Permission> permission = permissionRepository.findByEndpointAndMethodAndActiveTrue(endpoint, method);
        
        // If endpoint not in permission table, allow access for all authenticated users
        if (permission.isEmpty()) {
            return true;
        }
        
        // Get current user
        Optional<AppUser> currentUser = SecurityUtil.getCurrentUser();
        if (currentUser.isEmpty()) {
            return false;
        }
        
        // Check if user's role has permission for this endpoint
        String userRoleName = currentUser.get().getRole().getName();
        Optional<Permission> rolePermission = permissionRepository.findByEndpointAndMethodAndRoleName(endpoint, method, userRoleName);
        
        return rolePermission.isPresent();
    }
}
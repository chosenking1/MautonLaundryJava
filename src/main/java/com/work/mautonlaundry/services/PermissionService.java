package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionService {
    
    @Autowired
    private UserRepository userRepository;
    
    public boolean hasPermission(String userEmail, String resource, String action) {
        AppUser user = userRepository.findUserByEmail(userEmail).orElse(null);
        if (user == null || user.getRole() == null) return false;
        
        // Check if the user's role has the required permission
        String requiredPermissionName = resource + "_" + action;
        Optional<Permission> matchingPermission = user.getRole().getPermissions().stream()
                .filter(p -> p.getName().equals(requiredPermissionName))
                .findFirst();
        
        return matchingPermission.isPresent();
    }
    
    public boolean hasPermission(String userEmail, String permissionName) {
        AppUser user = userRepository.findUserByEmail(userEmail).orElse(null);
        if (user == null || user.getRole() == null) return false;
        
        // Check if the user's role has the required permission
        Optional<Permission> matchingPermission = user.getRole().getPermissions().stream()
                .filter(p -> p.getName().equals(permissionName))
                .findFirst();
        
        return matchingPermission.isPresent();
    }
}

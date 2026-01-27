package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RoleService {
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PermissionRepository permissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public void assignRoleToUser(String userEmail, String roleName) {
        AppUser user = userRepository.findUserByEmail(userEmail).orElseThrow();
        Role role = roleRepository.findByName(roleName).orElseThrow();
        // Role operations not supported with UserRole enum
        userRepository.save(user);
    }
    
    @Transactional
    public void removeRoleFromUser(String userEmail, String roleName) {
        AppUser user = userRepository.findUserByEmail(userEmail).orElseThrow();
        Role role = roleRepository.findByName(roleName).orElseThrow();
        // Role operations not supported with UserRole enum
        userRepository.save(user);
    }
    
    @Transactional
    public void addPermissionToRole(String roleName, String permissionName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        Permission permission = permissionRepository.findByName(permissionName).orElseThrow();
        role.getPermissions().add(permission);
        roleRepository.save(role);
    }
}
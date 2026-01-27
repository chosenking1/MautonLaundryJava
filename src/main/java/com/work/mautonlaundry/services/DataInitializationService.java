package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;

@Service
public class DataInitializationService implements CommandLineRunner {
    
    @Autowired
    private PermissionRepository permissionRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Override
    public void run(String... args) {
        initializePermissions();
        initializeRoles();
    }
    
    private void initializePermissions() {
        String[][] permissions = {
            {"USER_CREATE", "Create users", "USER", "CREATE"},
            {"USER_READ", "Read users", "USER", "READ"},
            {"USER_UPDATE", "Update users", "USER", "UPDATE"},
            {"USER_DELETE", "Delete users", "USER", "DELETE"},
            {"BOOKING_CREATE", "Create bookings", "BOOKING", "CREATE"},
            {"BOOKING_READ", "Read bookings", "BOOKING", "READ"},
            {"BOOKING_UPDATE", "Update bookings", "BOOKING", "UPDATE"},
            {"BOOKING_DELETE", "Delete bookings", "BOOKING", "DELETE"},
            {"PAYMENT_CREATE", "Create payments", "PAYMENT", "CREATE"},
            {"PAYMENT_READ", "Read payments", "PAYMENT", "READ"},
            {"DELIVERY_READ", "Read deliveries", "DELIVERY", "READ"},
            {"DELIVERY_UPDATE", "Update deliveries", "DELIVERY", "UPDATE"}
        };
        
        for (String[] perm : permissions) {
            if (!permissionRepository.findByName(perm[0]).isPresent()) {
                Permission permission = new Permission();
                permission.setName(perm[0]);
                permission.setDescription(perm[1]);
                permission.setResource(perm[2]);
                permission.setAction(perm[3]);
                permissionRepository.save(permission);
            }
        }
    }
    
    private void initializeRoles() {
        // ADMIN role
        if (!roleRepository.findByName("ADMIN").isPresent()) {
            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("Administrator with full access");
            adminRole.setPermissions(new HashSet<>(permissionRepository.findAll()));
            roleRepository.save(adminRole);
        }
        
        // USER role
        if (!roleRepository.findByName("USER").isPresent()) {
            Role userRole = new Role();
            userRole.setName("USER");
            userRole.setDescription("Regular user");
            userRole.setPermissions(new HashSet<>(Arrays.asList(
                permissionRepository.findByName("BOOKING_CREATE").orElse(null),
                permissionRepository.findByName("BOOKING_READ").orElse(null),
                permissionRepository.findByName("PAYMENT_READ").orElse(null)
            )));
            roleRepository.save(userRole);
        }
        
        // LAUNDRY_AGENT role
        if (!roleRepository.findByName("LAUNDRY_AGENT").isPresent()) {
            Role agentRole = new Role();
            agentRole.setName("LAUNDRY_AGENT");
            agentRole.setDescription("Laundry processing agent");
            agentRole.setPermissions(new HashSet<>(Arrays.asList(
                permissionRepository.findByName("BOOKING_READ").orElse(null),
                permissionRepository.findByName("BOOKING_UPDATE").orElse(null),
                permissionRepository.findByName("DELIVERY_READ").orElse(null)
            )));
            roleRepository.save(agentRole);
        }
        
        // DELIVERY_AGENT role
        if (!roleRepository.findByName("DELIVERY_AGENT").isPresent()) {
            Role deliveryRole = new Role();
            deliveryRole.setName("DELIVERY_AGENT");
            deliveryRole.setDescription("Delivery agent");
            deliveryRole.setPermissions(new HashSet<>(Arrays.asList(
                permissionRepository.findByName("DELIVERY_READ").orElse(null),
                permissionRepository.findByName("DELIVERY_UPDATE").orElse(null),
                permissionRepository.findByName("BOOKING_READ").orElse(null)
            )));
            roleRepository.save(deliveryRole);
        }
    }
}
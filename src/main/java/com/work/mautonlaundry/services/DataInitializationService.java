package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;

@Service
public class DataInitializationService implements CommandLineRunner {
    
    @Autowired
    private PermissionRepository permissionRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) {
        initializePermissions();
        initializeRoles();
        initializeAdminUser();
    }
    
    private void initializePermissions() {
        String[][] permissions = {
            {"USER_CREATE", "USER", "CREATE", "Create users", "/api/v1/admin/users", "POST"},
            {"USER_READ", "USER", "READ", "Read users", "/api/v1/admin/users", "GET"},
            {"USER_UPDATE", "USER", "UPDATE", "Update users", "/api/v1/admin/users", "PUT"},
            {"USER_DELETE", "USER", "DELETE", "Delete users", "/api/v1/admin/users", "DELETE"},
            {"BOOKING_CREATE", "BOOKING", "CREATE", "Create bookings", "/api/v1/bookings", "POST"},
            {"BOOKING_READ", "BOOKING", "READ", "Read bookings", "/api/v1/bookings", "GET"},
            {"BOOKING_UPDATE", "BOOKING", "UPDATE", "Update bookings", "/api/v1/bookings", "PUT"},
            {"BOOKING_DELETE", "BOOKING", "DELETE", "Delete bookings", "/api/v1/bookings", "DELETE"},
            {"PAYMENT_CREATE", "PAYMENT", "CREATE", "Create payments", "/api/v1/payments", "POST"},
            {"PAYMENT_READ", "PAYMENT", "READ", "Read payments", "/api/v1/payments", "GET"},
            {"DELIVERY_READ", "DELIVERY", "READ", "Read deliveries", "/api/v1/delivery", "GET"},
            {"DELIVERY_UPDATE", "DELIVERY", "UPDATE", "Update deliveries", "/api/v1/delivery", "PUT"},
            {"SERVICE_CREATE", "SERVICE", "CREATE", "Create services", "/api/v1/services", "POST"},
            {"SERVICE_READ", "SERVICE", "READ", "Read services", "/api/v1/services", "GET"},
            {"SERVICE_UPDATE", "SERVICE", "UPDATE", "Update services", "/api/v1/services", "PUT"},
            {"SERVICE_DELETE", "SERVICE", "DELETE", "Delete services", "/api/v1/services", "DELETE"}
        };
        
        for (String[] perm : permissions) {
            if (!permissionRepository.findByName(perm[0]).isPresent()) {
                Permission permission = new Permission();
                permission.setName(perm[0]);
                permission.setResource(perm[1]);
                permission.setAction(perm[2]);
                permission.setDescription(perm[3]);
                permission.setEndpoint(perm[4]);
                permission.setMethod(perm[5]);
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
        } else {
            // Update existing ADMIN role with new permissions
            Role adminRole = roleRepository.findByName("ADMIN").get();
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
    
    private void initializeAdminUser() {
        if (!userRepository.existsByEmail("Admin@mail.com")) {
            AppUser adminUser = new AppUser();
            adminUser.setEmail("Admin@mail.com");
            adminUser.setPassword(passwordEncoder.encode("password"));
            adminUser.setFull_name("Admin User");
            adminUser.setAddress("Admin Address");
            adminUser.setPhone_number("1234567890");
            adminUser.setEmailVerified(true);
            
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
            adminUser.setRole(adminRole);
            
            userRepository.save(adminUser);
        }
    }
}

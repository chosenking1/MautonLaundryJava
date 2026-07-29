package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;

@Service
public class DataInitializationService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializationService.class);

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserScopeRepository userScopeRepository;

    @Autowired
    private PermissionEvaluationService permissionEvaluationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;
    
    @Override
    public void run(String... args) {
        initializePermissions();
        initializeRoles();
        initializeAdminUser();

        // Last, and only after the grants above are settled. Redis survives a
        // deploy, so a permission set cached before this boot would otherwise
        // linger for the cache TTL — long enough to deny an admin an endpoint
        // whose permission this very startup just granted them.
        long flushed = permissionEvaluationService.invalidateAll();
        if (flushed > 0) {
            log.info("Flushed {} cached permission set(s) after startup sync", flushed);
        }
    }
    
    private void initializePermissions() {
        String[][] permissions = {
            // User Permissions
            {"USER_CREATE", "Create users", "USER", "CREATE", "/api/v1/users", "POST"},
            {"USER_READ", "Read users", "USER", "READ", "/api/v1/users/**", "GET"},
            {"USER_UPDATE", "Update users", "USER", "UPDATE", "/api/v1/users/**", "PUT"},
            {"USER_DELETE", "Delete users", "USER", "DELETE", "/api/v1/users/**", "DELETE"},
            
            // Address Permissions
            {"ADDRESS_CREATE", "Create addresses", "ADDRESS", "CREATE", "/api/v1/addresses", "POST"},
            {"ADDRESS_READ", "Read addresses", "ADDRESS", "READ", "/api/v1/addresses/**", "GET"},
            {"ADDRESS_UPDATE", "Update addresses", "ADDRESS", "UPDATE", "/api/v1/addresses/**", "PUT"},
            {"ADDRESS_DELETE", "Delete addresses", "ADDRESS", "DELETE", "/api/v1/addresses/**", "DELETE"},
            
            // Booking Permissions
            {"BOOKING_CREATE", "Create bookings", "BOOKING", "CREATE", "/api/v1/bookings", "POST"},
            {"BOOKING_READ", "Read bookings", "BOOKING", "READ", "/api/v1/bookings/**", "GET"},
            {"BOOKING_UPDATE", "Update bookings", "BOOKING", "UPDATE", "/api/v1/bookings/**", "PUT"},
            {"BOOKING_DELETE", "Delete bookings", "BOOKING", "DELETE", "/api/v1/bookings/**", "DELETE"},
            
            // Payment Permissions
            {"PAYMENT_CREATE", "Create payments", "PAYMENT", "CREATE", "/api/v1/payments", "POST"},
            {"PAYMENT_READ", "Read payments", "PAYMENT", "READ", "/api/v1/payments/**", "GET"},
            {"PAYMENT_UPDATE", "Update payments", "PAYMENT", "UPDATE", "/api/v1/payments/**", "PATCH"},
            
            // Delivery Permissions
            {"DELIVERY_READ", "Read deliveries", "DELIVERY", "READ", "/api/v1/deliveries/**", "GET"},
            {"DELIVERY_UPDATE", "Update deliveries", "DELIVERY", "UPDATE", "/api/v1/deliveries/**", "PUT"},

            // Analytics Permissions
            {"ANALYTICS_READ", "Read analytics", "ANALYTICS", "READ", "/api/v1/admin/analytics/**", "GET"},

            // Role Change Permissions
            {"ROLE_CHANGE_CREATE", "Create role change requests", "ROLE_CHANGE", "CREATE", "/api/v1/admin/role-requests", "POST"},
            {"ROLE_CHANGE_UPDATE", "Approve or reject role change requests", "ROLE_CHANGE", "UPDATE", "/api/v1/admin/role-requests/**", "PATCH"},
            {"ROLE_CHANGE_READ", "Read pending role change requests", "ROLE_CHANGE", "READ", "/api/v1/admin/role-requests/pending", "GET"},

            // Access Control Permissions
            {"PERMISSION_CREATE", "Create permissions", "PERMISSION", "CREATE", "/api/v1/permissions", "POST"},
            {"ROLE_CREATE", "Create roles", "ROLE", "CREATE", "/api/v1/roles", "POST"},
            {"ROLE_PERMISSION_ASSIGN", "Assign permissions to roles", "ROLE", "ASSIGN_PERMISSION", "/api/v1/roles/*/permissions", "POST"}
        };
        
        for (String[] perm : permissions) {
            if (!permissionRepository.findByName(perm[0]).isPresent()) {
                Permission permission = new Permission();
                permission.setName(perm[0]);
                permission.setDescription(perm[1]);
                permission.setResource(perm[2]);
                permission.setAction(perm[3]);
                permission.setEndpoint(perm[4]);
                permission.setMethod(perm[5]);
                permissionRepository.save(permission);
            }
        }
    }
    
    private void initializeRoles() {
        // ADMIN role (always sync to include newly added permissions)
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setName("ADMIN");
            role.setDescription("Administrator with full access");
            return role;
        });
        adminRole.setPermissions(new HashSet<>(permissionRepository.findAll()));
        roleRepository.save(adminRole);
        
        // USER role (always sync to include newly added permissions)
        Role userRole = roleRepository.findByName("USER").orElseGet(() -> {
            Role role = new Role();
            role.setName("USER");
            role.setDescription("Regular user");
            return role;
        });
        userRole.setPermissions(new HashSet<>(Arrays.asList(
                permissionRepository.findByName("BOOKING_CREATE").orElse(null),
                permissionRepository.findByName("BOOKING_READ").orElse(null),
                permissionRepository.findByName("PAYMENT_CREATE").orElse(null),
                permissionRepository.findByName("PAYMENT_READ").orElse(null),
                permissionRepository.findByName("ADDRESS_CREATE").orElse(null),
                permissionRepository.findByName("ADDRESS_READ").orElse(null),
                permissionRepository.findByName("ADDRESS_UPDATE").orElse(null),
                permissionRepository.findByName("ADDRESS_DELETE").orElse(null)
        )));
        roleRepository.save(userRole);
        
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
        if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()
                || bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            return;
        }

        // Trim once. The existence check used to read the raw property while the
        // insert wrote a trimmed one, so a stray space in BOOTSTRAP_ADMIN_EMAIL
        // meant the check never matched and every boot retried the insert.
        String email = bootstrapAdminEmail.trim();

        AppUser adminUser = userRepository.findUserByEmail(email).orElse(null);

        if (adminUser == null) {
            adminUser = new AppUser();
            adminUser.setEmail(email);
            adminUser.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
            adminUser.setFull_name("Bootstrap Admin");
            adminUser.setPhone_number("0000000000");
            adminUser.setEmailVerified(true);

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
            adminUser.setRole(adminRole);

            adminUser = userRepository.save(adminUser);
        }

        // Runs for an existing admin too, not just a freshly created one: the
        // early return this replaced meant an admin created after
        // V17__seed_user_scope.sql never got a scope at all.
        ensureNationalScope(adminUser);
    }

    /**
     * Guarantees the bootstrap admin has a data scope
     * (Permission Architecture V2, spec §5).
     *
     * <p>ScopeFilterService is closed-by-default: a user with no user_scope row
     * sees nothing. V17 seeds the admins that existed when it ran, but the
     * bootstrap admin can be created after it — on a newly provisioned
     * environment, that would produce an administrator who can see none of the
     * system they are meant to administer, with no error to explain why.
     *
     * <p>Never overwrites an existing row. If an admin has been narrowed to,
     * say, STATE scope deliberately, a reboot must not silently promote them
     * back to NATIONAL. Same rule as V17's ON CONFLICT DO NOTHING: a human
     * decision beats a default.
     */
    private void ensureNationalScope(AppUser admin) {
        if (admin.getId() == null || userScopeRepository.existsById(admin.getId())) {
            return;
        }

        UserScope scope = new UserScope();
        scope.setUserId(admin.getId());
        scope.setScopeLevel(ScopeLevel.NATIONAL);
        // assigned_by stays null, meaning system-seeded (see V15).
        scope.setAssignedAt(LocalDateTime.now());
        userScopeRepository.save(scope);

        log.info("Assigned NATIONAL scope to bootstrap admin {}", admin.getEmail());
    }
}

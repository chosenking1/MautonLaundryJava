package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.security.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserPermissionService {
    
    public Map<String, Object> getUserPermissions() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "NO_ROLE";
        
        Map<String, Object> response = new HashMap<>();
        response.put("role", roleName);
        response.put("permissions", getUserPermissionsList(currentUser));
        response.put("pages", getAccessiblePages(currentUser));
        
        return response;
    }
    
    private List<String> getUserPermissionsList(AppUser user) {
        Set<String> permissions = new HashSet<>();
        if (user.getRole() != null) {
            permissions.add("READ"); // Default permission
            for (Permission permission : user.getRole().getPermissions()) {
                permissions.add(permission.getName());
            }
        }
        return new ArrayList<>(permissions);
    }
    
    private Map<String, Boolean> getAccessiblePages(AppUser user) {
        Map<String, Boolean> pages = new HashMap<>();
        
        // Common pages for all authenticated users
        pages.put("profile", true);
        pages.put("notifications", true);
        pages.put("settings", true);
        
        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            switch (roleName) {
                case "ADMIN":
                    pages.put("dashboard", true);
                    pages.put("analytics", true);
                    pages.put("user_management", true);
                    pages.put("booking_management", true);
                    pages.put("payment_management", true);
                    pages.put("delivery_management", true);
                    pages.put("service_management", true);
                    pages.put("reports", true);
                    pages.put("system_settings", true);
                    break;
                    
                case "CUSTOMER":
                    pages.put("dashboard", true);
                    pages.put("book_service", true);
                    pages.put("my_bookings", true);
                    pages.put("booking_history", true);
                    pages.put("payment_history", true);
                    pages.put("track_delivery", true);
                    break;
                    
                case "LAUNDRY_AGENT":
                    pages.put("dashboard", true);
                    pages.put("process_orders", true);
                    pages.put("booking_list", true);
                    pages.put("update_status", true);
                    pages.put("scan_qr", true);
                    break;
                    
                case "DELIVERY_AGENT":
                    pages.put("dashboard", true);
                    pages.put("delivery_list", true);
                    pages.put("update_delivery", true);
                    pages.put("route_planning", true);
                    pages.put("scan_qr", true);
                    break;
            }
        }
        
        return pages;
    }
}
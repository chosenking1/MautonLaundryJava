package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.AdminDashboardResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN_DASHBOARD_READ')") // Updated to permission-based check
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        AdminDashboardResponse dashboard = new AdminDashboardResponse();
        dashboard.setTotalUsers(userRepository.count());
        dashboard.setActiveUsers(userRepository.countByDeletedFalse());
        dashboard.setVerifiedUsers(userRepository.countByEmailVerifiedTrue());
        
        return ResponseEntity.ok(dashboard);
    }
}
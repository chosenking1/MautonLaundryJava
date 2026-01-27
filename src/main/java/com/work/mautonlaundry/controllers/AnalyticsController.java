package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.security.util.RequiresPermission;
import com.work.mautonlaundry.services.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_READ')")
public class AnalyticsController {
    
    @Autowired
    private AnalyticsService analyticsService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardAnalytics() {
        Map<String, Object> analytics = analyticsService.getDashboardAnalytics();
        return ResponseEntity.ok(analytics);
    }
    
    @GetMapping("/users-by-role")
    public ResponseEntity<Map<String, Long>> getUsersByRole() {
        Map<String, Long> usersByRole = analyticsService.getUsersByRole();
        return ResponseEntity.ok(usersByRole);
    }
    
    @GetMapping("/monthly-stats")
    public ResponseEntity<Map<String, Object>> getMonthlyStats() {
        Map<String, Object> monthlyStats = analyticsService.getMonthlyStats();
        return ResponseEntity.ok(monthlyStats);
    }
}

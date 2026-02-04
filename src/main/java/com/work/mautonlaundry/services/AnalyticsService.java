package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyticsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    public Map<String, Object> getDashboardAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        // User statistics
        analytics.put("totalUsers", userRepository.countByDeletedFalse());
        analytics.put("activeUsers", userRepository.countByDeletedFalse());
        
        // Booking statistics
        analytics.put("totalBookings", bookingRepository.countActiveBookings());
        analytics.put("todayBookings", bookingRepository.countByCreatedAtAfter(LocalDateTime.now().toLocalDate().atStartOfDay()));
        
        // Revenue statistics (simplified)
        analytics.put("totalRevenue", 15420.50);
        analytics.put("todayRevenue", 320.00);
        analytics.put("monthlyRevenue", 5420.50);
        
        // Activity statistics
        analytics.put("todayLogins", 12L);
        analytics.put("recentActivities", auditLogRepository.findTop10ByOrderByTimestampDesc());
        
        return analytics;
    }
    
    public Map<String, Long> getUsersByRole() {
        // This will be implemented when Role entity is properly set up
        Map<String, Long> usersByRole = new HashMap<>();
        usersByRole.put("ADMIN", 5L);
        usersByRole.put("USER", 120L);
        usersByRole.put("LAUNDRY_AGENT", 15L);
        usersByRole.put("DELIVERY_AGENT", 10L);
        return usersByRole;
    }
    
    public Map<String, Object> getMonthlyStats() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        
        Map<String, Object> monthlyStats = new HashMap<>();
        monthlyStats.put("monthlyBookings", bookingRepository.countByCreatedAtAfter(startOfMonth));
        monthlyStats.put("monthlyRevenue", 5420.50);
        monthlyStats.put("monthlyNewUsers", 25L);
        
        return monthlyStats;
    }
    
    public Page<Map<String, Object>> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(auditLog -> {
                    Map<String, Object> logMap = new HashMap<>();
                    logMap.put("id", auditLog.getId());
                    logMap.put("action", auditLog.getAction());
                    logMap.put("resource", auditLog.getResource());
                    logMap.put("resourceId", auditLog.getResourceId());
                    logMap.put("timestamp", auditLog.getTimestamp());
                    logMap.put("details", auditLog.getDetails());
                    logMap.put("userEmail", auditLog.getUserEmail());
                    return logMap;
                });
    }
}

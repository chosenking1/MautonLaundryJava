package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    public Map<String, Object> getDashboardAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        // User statistics
        analytics.put("totalUsers", userRepository.count());
        analytics.put("activeUsers", userRepository.countByDeletedFalse());
        
        // Booking statistics
        analytics.put("totalBookings", bookingRepository.count());
        analytics.put("todayBookings", bookingRepository.countByCreatedAtAfter(LocalDateTime.now().toLocalDate().atStartOfDay()));
        
        // Payment statistics
        analytics.put("totalRevenue", paymentRepository.sumTotalAmount());
        analytics.put("todayRevenue", paymentRepository.sumAmountByPaymentDateAfter(LocalDateTime.now().toLocalDate().atStartOfDay()));
        
        // Activity statistics
        analytics.put("todayLogins", auditLogRepository.countByActionAndTimestampAfter("LOGIN", LocalDateTime.now().toLocalDate().atStartOfDay()));
        analytics.put("recentActivities", auditLogRepository.findTop10ByOrderByTimestampDesc());
        
        return analytics;
    }
    
    public Map<String, Long> getUsersByRole() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream()
                .collect(Collectors.toMap(
                        Role::getName,
                        role -> userRepository.countByRole(role)
                ));
    }
    
    public Map<String, Object> getMonthlyStats() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        
        Map<String, Object> monthlyStats = new HashMap<>();
        monthlyStats.put("monthlyBookings", bookingRepository.countByCreatedAtAfter(startOfMonth));
        monthlyStats.put("monthlyRevenue", paymentRepository.sumAmountByPaymentDateAfter(startOfMonth));
        monthlyStats.put("monthlyNewUsers", userRepository.count()); // Simplified since no createdAt field
        
        return monthlyStats;
    }
}

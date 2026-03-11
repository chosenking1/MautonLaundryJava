package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardAnalyticsResponse {
    private Long totalUsers;
    private Long activeUsers;
    private Long totalBookings;
    private Long todayBookings;
    private BigDecimal totalRevenue;
    private BigDecimal todayRevenue;
    private BigDecimal monthlyRevenue;
    private Long todayLogins;
    private List<AuditLogResponse> recentActivities;
    
    // Bookings breakdown
    private List<BookingsByStatus> bookingsByStatus;
    
    // Revenue breakdown
    private List<RevenueByPaymentMethod> revenueByPaymentMethod;
    private List<RevenueByServiceCategory> revenueByServiceCategory;
    
    // User behavior metrics
    private Long newUsersToday;
    private Long returningUsersToday;
    private Double retention7d;
    
    // Operational / quality metrics
    private Double onTimeDeliveryRate;
    private Double avgTurnaroundHours;
    private Long failedPayments;
    private Long refundedPayments;
}

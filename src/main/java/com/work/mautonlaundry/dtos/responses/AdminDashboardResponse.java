package com.work.mautonlaundry.dtos.responses;

import lombok.Data;

@Data
public class AdminDashboardResponse {
    private Long totalUsers;
    private Long activeUsers;
    private Long verifiedUsers;
    private Long totalBookings;
    private Long pendingBookings;
    private Long completedBookings;
}
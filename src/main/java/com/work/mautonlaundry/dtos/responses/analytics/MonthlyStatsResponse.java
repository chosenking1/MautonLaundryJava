package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyStatsResponse {
    private long monthlyBookings;
    private BigDecimal monthlyRevenue;
    private long monthlyNewUsers;
}

package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The CEO/management Executive Dashboard payload. Every time-based metric carries
 * its previous-period comparison. Values are computed live from transactional
 * tables (correct and fast at launch volumes); the daily snapshot table backs
 * future scale and trend history.
 */
public record ExecutiveDashboardResponse(
        Revenue revenue,
        Customers customers,
        Growth growth,
        Operational operational,
        Rankings rankings,
        LocalDateTime generatedAt
) {
    public record Revenue(
            MoneyComparison revenueToday,        // vs same day last week
            MoneyComparison revenueThisWeek,     // vs last week
            MoneyComparison revenueThisMonth,    // vs last month
            BigDecimal revenueThisYear,          // YTD
            BigDecimal platformEarningsToday,
            MoneyComparison platformEarningsThisMonth,
            BigDecimal platformEarningsThisYear
    ) {}

    public record Customers(
            long totalCustomers,                 // registered users with >= 1 order
            long activeCustomers,                // ordered in last 30 days
            CountComparison newCustomersThisMonth,      // first order this month, vs last month
            CountComparison returningCustomersThisMonth, // ordered this month + a previous month
            long inactiveCustomers               // no order in > 45 days (churn risk)
    ) {}

    public record Growth(
            Double revenueGrowthRate,
            Double customerGrowthRate,
            Double repeatCustomerRate,
            Double ordersGrowthRate
    ) {}

    public record Operational(
            long activeLaundrymen,
            long activeCas,
            CountComparison ordersToday,         // vs yesterday
            CountComparison ordersThisMonth,     // vs last month
            BigDecimal averageOrderValue         // this month revenue / this month orders
    ) {}

    public record Rankings(
            List<RankEntry> topCasByRevenueThisMonth,
            List<RankEntry> topCustomersBySpendThisMonth,
            List<RankEntry> topCustomersByLifetimeSpend
    ) {}
}

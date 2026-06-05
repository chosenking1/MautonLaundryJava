package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full business profile for one customer. PII (email/phone) is masked. Status is
 * the profile-level classification (HEALTHY / WATCHLIST / AT_RISK / HIGH_CHURN_RISK).
 */
public record CustomerProfileResponse(
        Identity identity,
        Revenue revenue,
        Behaviour behaviour,
        String status,
        List<OrderHistoryItem> orderHistory
) {
    public record Identity(
            String name,
            String email,           // masked
            String phone,           // masked
            String city,
            String state,
            LocalDateTime registrationDate,
            long accountAgeDays,
            String acquisitionSource,   // CAS name or "Organic"
            String referralCode         // null when organic
    ) {}

    public record Revenue(
            BigDecimal lifetimeSpend,
            BigDecimal lifetimePlatformEarnings,
            BigDecimal averageMonthlySpend,
            BigDecimal averageOrderValue,
            MoneyComparison spendThisVsLastMonth
    ) {}

    public record Behaviour(
            long totalOrders,
            long ordersThisMonth,
            long ordersLastMonth,
            Long daysSinceLastOrder,        // null if never ordered
            Double averageDaysBetweenOrders, // null if < 2 orders
            Long longestGapDays              // null if < 2 orders
    ) {}

    public record OrderHistoryItem(
            String bookingId,
            LocalDateTime date,
            String status,
            BigDecimal total,
            String laundryman,       // null if not yet assigned
            List<String> items
    ) {}
}

package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full performance breakdown for one CAS (SPECIALIST referrer). The referred-
 * customer list is anonymised ("Customer #N") — never real names or emails.
 */
public record CasProfileResponse(
        String referrerId,
        String name,
        String referralCode,
        Acquisition acquisition,
        Revenue revenue,
        Quality quality,
        Milestone milestone,
        List<AcquiredByMonth> acquiredByMonth,
        List<CasCustomer> customers
) {
    public record Acquisition(long totalAcquired, long newThisMonth, long newLastMonth) {}

    public record Revenue(
            BigDecimal revenueLifetime,
            BigDecimal revenueThisMonth,
            BigDecimal platformEarningsLifetime,
            BigDecimal platformEarningsThisMonth
    ) {}

    public record Quality(
            BigDecimal avgCustomerSpendPerMonth,
            Double avgOrderFrequencyPerMonth,
            Double activeCustomerRate,
            Double repeatCustomerRate
    ) {}

    public record Milestone(
            long currentRepeat,
            int target,
            long remaining,
            Double estimatedMonthsToMilestone,   // null if not estimable
            String statusLabel                    // NOT_STARTED / IN_PROGRESS / NEAR_MILESTONE / ELIGIBLE / ACTIVE
    ) {}

    public record AcquiredByMonth(String month, long count) {}

    public record CasCustomer(
            String reference,            // "Customer #N" — anonymised
            LocalDateTime registrationDate,
            long totalOrders,
            LocalDateTime lastOrderDate,
            BigDecimal spend,
            String status
    ) {}
}

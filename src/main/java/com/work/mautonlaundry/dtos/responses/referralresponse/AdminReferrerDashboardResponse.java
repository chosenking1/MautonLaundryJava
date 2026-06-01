package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;

/** Aggregate stats for a single referrer (admin detail view & in-app summary share the figures). */
public record AdminReferrerDashboardResponse(
        String referrerId,
        long totalCustomers,
        long repeatCustomers,
        MilestoneProgressResponse milestone,
        BigDecimal totalEarned,
        BigDecimal totalPaid,
        BigDecimal pendingBalance
) {
}

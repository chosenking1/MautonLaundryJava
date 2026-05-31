package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;
import java.util.List;

/** The in-app referral dashboard for a logged-in user who is also a referrer. */
public record MyReferralDashboardResponse(
        String referralCode,
        long totalCustomersReferred,
        long repeatCustomers,
        BigDecimal totalEarned,
        BigDecimal pendingPayout,
        MilestoneProgressResponse milestone,
        List<ReferralActivityResponse> recentActivity,
        List<PayoutResponse> paymentHistory
) {
}

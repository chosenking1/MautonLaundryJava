package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;

/**
 * One row in the CAS (Customer Acquisition Specialist) performance list. Quality
 * metrics (active/repeat/retention/revenue) matter more than raw acquisition count.
 * status: COMMISSION_ONLY / SALARY_ELIGIBLE / SALARY_ACTIVE.
 */
public record CasListItemResponse(
        String referrerId,
        String name,
        String referralCode,
        long customersAcquired,
        long activeCustomers,
        long repeatCustomers,
        int milestoneTarget,
        BigDecimal revenueGenerated,
        BigDecimal platformEarnings,
        Double retentionRate,
        String status
) {
}

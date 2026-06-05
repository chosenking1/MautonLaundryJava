package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row in the Customer Intelligence list. Phone/email are masked for privacy.
 * Status is recency-based (ACTIVE/INACTIVE/AT_RISK/CHURNED). Acquisition source is
 * the referring CAS name, or "Organic".
 */
public record CustomerListItemResponse(
        String userId,
        String name,
        String phone,            // masked: first 4 + last 2 digits
        String city,
        LocalDateTime customerSince,
        long totalOrders,
        BigDecimal lifetimeSpend,
        BigDecimal platformEarnings,
        LocalDateTime lastOrderDate,
        String status,
        String acquisitionSource
) {
}

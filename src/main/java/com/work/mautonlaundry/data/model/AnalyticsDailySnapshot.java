package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pre-aggregated dashboard metrics for a single completed day. Written by the
 * midnight (Africa/Lagos) snapshot job; read by the Executive Dashboard for
 * historical/trend metrics. See V7 migration for column semantics.
 */
@Entity
@Table(name = "analytics_daily_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDailySnapshot {

    @Id
    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_revenue", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "platform_earnings", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal platformEarnings = BigDecimal.ZERO;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "new_customers", nullable = false)
    @Builder.Default
    private Integer newCustomers = 0;

    @Column(name = "active_customers", nullable = false)
    @Builder.Default
    private Integer activeCustomers = 0;

    @Column(name = "returning_customers", nullable = false)
    @Builder.Default
    private Integer returningCustomers = 0;

    @Column(name = "average_order_value", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal averageOrderValue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

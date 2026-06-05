package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AnalyticsDailySnapshot;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.AnalyticsDailySnapshotRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Computes and persists the pre-aggregated {@link AnalyticsDailySnapshot} for a
 * single day. Idempotent: re-running for the same date overwrites the row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsSnapshotService {

    private static final int ACTIVE_WINDOW_DAYS = 30;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PricingEngine pricingEngine;
    private final AnalyticsDailySnapshotRepository snapshotRepository;

    @Transactional
    public AnalyticsDailySnapshot generateSnapshot(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        BigDecimal revenue = nz(paymentRepository.sumAmountByStatusAndDateBetween(
                PaymentStatus.COMPLETED, dayStart, dayEnd));
        long orders = nz(bookingRepository.countBetween(dayStart, dayEnd));
        BigDecimal earnings = revenue.multiply(pricingEngine.getImototoCommissionRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal aov = orders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);

        long newCustomers = nz(userRepository.countFirstTimeCustomersBetween(dayStart, dayEnd));
        long returningCustomers = nz(userRepository.countReturningCustomersBetween(dayStart, dayEnd));
        // Active = ordered in the trailing 30 days as of end of this day.
        long activeCustomers = nz(userRepository.countUsersWithBookingsBetween(
                dayEnd.minusDays(ACTIVE_WINDOW_DAYS), dayEnd));

        AnalyticsDailySnapshot snapshot = snapshotRepository.findById(date)
                .orElseGet(() -> AnalyticsDailySnapshot.builder().snapshotDate(date).build());
        snapshot.setTotalRevenue(revenue);
        snapshot.setPlatformEarnings(earnings);
        snapshot.setTotalOrders((int) orders);
        snapshot.setNewCustomers((int) newCustomers);
        snapshot.setReturningCustomers((int) returningCustomers);
        snapshot.setActiveCustomers((int) activeCustomers);
        snapshot.setAverageOrderValue(aov);
        snapshot.setCreatedAt(LocalDateTime.now());

        AnalyticsDailySnapshot saved = snapshotRepository.save(snapshot);
        log.info("Analytics snapshot for {}: revenue={}, earnings={}, orders={}, new={}, returning={}, active={}",
                date, revenue, earnings, orders, newCustomers, returningCustomers, activeCustomers);
        return saved;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }
}

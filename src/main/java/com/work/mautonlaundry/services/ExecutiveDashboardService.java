package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds the Executive Dashboard. All metrics are computed live from
 * transactional tables — correct and fast at launch volumes (backed by the new
 * indexes). The daily snapshot table is maintained in parallel for future scale
 * and trend history. Platform earnings use the configurable Imototo commission
 * rate (default 0.30), not a hardcoded percentage.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveDashboardService {

    private static final int ACTIVE_WINDOW_DAYS = 30;
    private static final int INACTIVE_THRESHOLD_DAYS = 45;
    private static final Pageable TOP_5 = PageRequest.of(0, 5);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final ReferralAttributionRepository referralAttributionRepository;
    private final PricingEngine pricingEngine;

    @Transactional(readOnly = true)
    public ExecutiveDashboardResponse getExecutiveDashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime startTomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime startYesterday = today.minusDays(1).atStartOfDay();
        LocalDateTime startSameDayLastWeek = today.minusDays(7).atStartOfDay();
        LocalDateTime endSameDayLastWeek = today.minusDays(6).atStartOfDay();

        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime startThisWeek = weekStart.atStartOfDay();
        LocalDateTime startNextWeek = weekStart.plusWeeks(1).atStartOfDay();
        LocalDateTime startLastWeek = weekStart.minusWeeks(1).atStartOfDay();

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime startThisMonth = monthStart.atStartOfDay();
        LocalDateTime startNextMonth = monthStart.plusMonths(1).atStartOfDay();
        LocalDateTime startLastMonth = monthStart.minusMonths(1).atStartOfDay();

        LocalDateTime startThisYear = today.withDayOfYear(1).atStartOfDay();
        LocalDateTime startNextYear = today.withDayOfYear(1).plusYears(1).atStartOfDay();

        LocalDateTime activeWindow = now.minusDays(ACTIVE_WINDOW_DAYS);
        LocalDateTime inactiveCutoff = now.minusDays(INACTIVE_THRESHOLD_DAYS);

        BigDecimal rate = pricingEngine.getImototoCommissionRate();

        // ---- Revenue ----
        BigDecimal revToday = rev(startToday, startTomorrow);
        BigDecimal revSameDayLastWeek = rev(startSameDayLastWeek, endSameDayLastWeek);
        BigDecimal revThisWeek = rev(startThisWeek, startNextWeek);
        BigDecimal revLastWeek = rev(startLastWeek, startThisWeek);
        BigDecimal revThisMonth = rev(startThisMonth, startNextMonth);
        BigDecimal revLastMonth = rev(startLastMonth, startThisMonth);
        BigDecimal revThisYear = rev(startThisYear, startNextYear);

        MoneyComparison revThisMonthCmp = MoneyComparison.of(revThisMonth, revLastMonth);
        ExecutiveDashboardResponse.Revenue revenue = new ExecutiveDashboardResponse.Revenue(
                MoneyComparison.of(revToday, revSameDayLastWeek),
                MoneyComparison.of(revThisWeek, revLastWeek),
                revThisMonthCmp,
                revThisYear,
                earnings(revToday, rate),
                MoneyComparison.of(earnings(revThisMonth, rate), earnings(revLastMonth, rate)),
                earnings(revThisYear, rate)
        );

        // ---- Customers ----
        long totalCustomers = nz(userRepository.countDistinctCustomers());
        long activeCustomers = nz(userRepository.countUsersWithBookingsAfter(activeWindow));
        long newThisMonth = nz(userRepository.countFirstTimeCustomersBetween(startThisMonth, startNextMonth));
        long newLastMonth = nz(userRepository.countFirstTimeCustomersBetween(startLastMonth, startThisMonth));
        long retThisMonth = nz(userRepository.countReturningCustomersBetween(startThisMonth, startNextMonth));
        long retLastMonth = nz(userRepository.countReturningCustomersBetween(startLastMonth, startThisMonth));
        long inactiveCustomers = nz(userRepository.countInactiveCustomers(inactiveCutoff));

        CountComparison newCmp = CountComparison.of(newThisMonth, newLastMonth);
        CountComparison retCmp = CountComparison.of(retThisMonth, retLastMonth);
        ExecutiveDashboardResponse.Customers customers = new ExecutiveDashboardResponse.Customers(
                totalCustomers, activeCustomers, newCmp, retCmp, inactiveCustomers);

        // ---- Orders / Operational ----
        long ordToday = nz(bookingRepository.countBetween(startToday, startTomorrow));
        long ordYesterday = nz(bookingRepository.countBetween(startYesterday, startToday));
        long ordThisMonth = nz(bookingRepository.countBetween(startThisMonth, startNextMonth));
        long ordLastMonth = nz(bookingRepository.countBetween(startLastMonth, startThisMonth));
        CountComparison ordTodayCmp = CountComparison.of(ordToday, ordYesterday);
        CountComparison ordMonthCmp = CountComparison.of(ordThisMonth, ordLastMonth);
        BigDecimal aov = ordThisMonth == 0
                ? BigDecimal.ZERO
                : revThisMonth.divide(BigDecimal.valueOf(ordThisMonth), 2, RoundingMode.HALF_UP);

        long activeLaundrymen = laundrymanAssignmentRepository.countActiveLaundrymenSince(activeWindow);
        long activeCas = referralAttributionRepository.countActiveReferrersWithType(ReferrerType.SPECIALIST, activeWindow);
        ExecutiveDashboardResponse.Operational operational = new ExecutiveDashboardResponse.Operational(
                activeLaundrymen, activeCas, ordTodayCmp, ordMonthCmp, aov);

        // ---- Growth (derived) ----
        Double repeatCustomerRate = activeCustomers == 0
                ? null
                : round1((double) retThisMonth / activeCustomers * 100);
        ExecutiveDashboardResponse.Growth growth = new ExecutiveDashboardResponse.Growth(
                revThisMonthCmp.changePercent(),
                newCmp.changePercent(),
                repeatCustomerRate,
                ordMonthCmp.changePercent());

        // ---- Rankings (Top 5) ----
        ExecutiveDashboardResponse.Rankings rankings = new ExecutiveDashboardResponse.Rankings(
                toRankEntries(paymentRepository.topCasByReferredRevenueInPeriod(
                        ReferrerType.SPECIALIST, PaymentStatus.COMPLETED, startThisMonth, startNextMonth, TOP_5)),
                toRankEntries(paymentRepository.topCustomersBySpendInPeriod(
                        PaymentStatus.COMPLETED, startThisMonth, startNextMonth, TOP_5)),
                toRankEntries(paymentRepository.topCustomersByLifetimeSpend(PaymentStatus.COMPLETED, TOP_5)));

        return new ExecutiveDashboardResponse(revenue, customers, growth, operational, rankings, now);
    }

    private BigDecimal rev(LocalDateTime start, LocalDateTime end) {
        return nz(paymentRepository.sumRevenueInPeriod(PaymentStatus.COMPLETED, start, end));
    }

    private BigDecimal earnings(BigDecimal revenue, BigDecimal rate) {
        return revenue.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private List<RankEntry> toRankEntries(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new RankEntry(
                        r[0] == null ? null : r[0].toString(),
                        r[1] == null ? "" : r[1].toString(),
                        toBigDecimal(r[2])))
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private Double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }
}

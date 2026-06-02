package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ReferralAttribution;
import com.work.mautonlaundry.data.model.Referrer;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.CasListItemResponse;
import com.work.mautonlaundry.dtos.responses.analytics.CasProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

/**
 * CAS (Customer Acquisition Specialist) performance. A CAS is a referrer of type
 * SPECIALIST; their referred customers + those customers' orders/payments drive
 * the quality metrics. Assembled from a few batch queries — fine at launch scale.
 */
@Service
@RequiredArgsConstructor
public class CasPerformanceService {

    private static final int ACTIVE_WINDOW_DAYS = 30;

    private final ReferrerRepository referrerRepository;
    private final ReferralAttributionRepository referralAttributionRepository;
    private final ReferralPaymentRuleRepository ruleRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PricingEngine pricingEngine;

    @Value("${app.referral.milestone-target:100}")
    private int milestoneTarget;

    @Transactional(readOnly = true)
    public List<CasListItemResponse> listCas(String sort, String direction, String search) {
        BigDecimal rate = pricingEngine.getImototoCommissionRate();
        LocalDateTime activeWindow = LocalDateTime.now().minusDays(ACTIVE_WINDOW_DAYS);

        // Batch maps keyed by customer userId.
        Map<String, Long> orderCount = new HashMap<>();
        Map<String, LocalDateTime> lastOrder = new HashMap<>();
        for (Object[] row : bookingRepository.aggregateOrdersPerCustomer()) {
            String uid = (String) row[0];
            orderCount.put(uid, row[1] == null ? 0L : ((Number) row[1]).longValue());
            lastOrder.put(uid, (LocalDateTime) row[2]);
        }
        Map<String, BigDecimal> spend = new HashMap<>();
        for (Object[] row : paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)) {
            spend.put((String) row[0], toBigDecimal(row[1]));
        }

        // referrerId -> referred userIds
        Map<String, List<String>> referredByReferrer = new HashMap<>();
        referralAttributionRepository.findAll().forEach(a ->
                referredByReferrer.computeIfAbsent(a.getReferrerId(), k -> new ArrayList<>()).add(a.getUserId()));

        Set<String> onSalary = new HashSet<>(ruleRepository.findReferrerIdsWithActiveRuleType(ReferralRuleType.MANUAL_OVERRIDE));

        List<Referrer> specialists = referrerRepository.findByReferrerType(ReferrerType.SPECIALIST, Pageable.unpaged()).getContent();

        List<CasListItemResponse> items = new ArrayList<>();
        for (Referrer r : specialists) {
            if (search != null && !search.isBlank()) {
                String term = search.toLowerCase();
                boolean match = (r.getName() != null && r.getName().toLowerCase().contains(term))
                        || (r.getReferralCode() != null && r.getReferralCode().toLowerCase().contains(term));
                if (!match) continue;
            }

            List<String> userIds = referredByReferrer.getOrDefault(r.getId(), List.of());
            long acquired = userIds.size();
            long active = 0;
            long repeat = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            for (String uid : userIds) {
                LocalDateTime last = lastOrder.get(uid);
                if (last != null && !last.isBefore(activeWindow)) active++;
                if (orderCount.getOrDefault(uid, 0L) >= 2) repeat++;
                revenue = revenue.add(spend.getOrDefault(uid, BigDecimal.ZERO));
            }
            Double retention = acquired == 0 ? null
                    : BigDecimal.valueOf((double) active / acquired * 100).setScale(1, RoundingMode.HALF_UP).doubleValue();
            String status = repeat >= milestoneTarget
                    ? (onSalary.contains(r.getId()) ? "SALARY_ACTIVE" : "SALARY_ELIGIBLE")
                    : "COMMISSION_ONLY";

            items.add(new CasListItemResponse(
                    r.getId(), r.getName(), r.getReferralCode(),
                    acquired, active, repeat, milestoneTarget,
                    revenue, revenue.multiply(rate).setScale(2, RoundingMode.HALF_UP),
                    retention, status));
        }

        items.sort(comparator(sort, direction));
        return items;
    }

    @Transactional(readOnly = true)
    public CasProfileResponse getCasProfile(String referrerId) {
        Referrer r = referrerRepository.findById(referrerId)
                .orElseThrow(() -> new NoSuchElementException("CAS not found"));

        BigDecimal rate = pricingEngine.getImototoCommissionRate();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDateTime startThisMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        LocalDateTime startLastMonth = today.withDayOfMonth(1).minusMonths(1).atStartOfDay();
        LocalDateTime activeWindow = now.minusDays(ACTIVE_WINDOW_DAYS);

        List<ReferralAttribution> attributions = new ArrayList<>(referralAttributionRepository.findByReferrerId(referrerId));
        attributions.sort(Comparator.comparing(ReferralAttribution::getRegisteredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<String> userIds = attributions.stream().map(ReferralAttribution::getUserId).toList();

        Map<String, Long> orderCount = new HashMap<>();
        Map<String, LocalDateTime> lastOrder = new HashMap<>();
        for (Object[] row : bookingRepository.aggregateOrdersPerCustomer()) {
            String uid = (String) row[0];
            orderCount.put(uid, row[1] == null ? 0L : ((Number) row[1]).longValue());
            lastOrder.put(uid, (LocalDateTime) row[2]);
        }
        Map<String, BigDecimal> spend = new HashMap<>();
        for (Object[] row : paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)) {
            spend.put((String) row[0], toBigDecimal(row[1]));
        }

        long totalAcquired = attributions.size();
        long active = 0, repeat = 0, totalOrders = 0;
        BigDecimal revenueLifetime = BigDecimal.ZERO;
        double totalTenureMonths = 0;
        for (ReferralAttribution a : attributions) {
            String uid = a.getUserId();
            long oc = orderCount.getOrDefault(uid, 0L);
            LocalDateTime last = lastOrder.get(uid);
            totalOrders += oc;
            if (last != null && !last.isBefore(activeWindow)) active++;
            if (oc >= 2) repeat++;
            revenueLifetime = revenueLifetime.add(spend.getOrDefault(uid, BigDecimal.ZERO));
            LocalDateTime regAt = a.getRegisteredAt() == null ? now : a.getRegisteredAt();
            totalTenureMonths += Math.max(1, ChronoUnit.MONTHS.between(regAt, now));
        }
        double avgTenureMonths = totalAcquired == 0 ? 1 : Math.max(1, totalTenureMonths / totalAcquired);

        long newThisMonth = attributions.stream().filter(a -> inPeriod(a.getRegisteredAt(), startThisMonth, startNextMonth)).count();
        long newLastMonth = attributions.stream().filter(a -> inPeriod(a.getRegisteredAt(), startLastMonth, startThisMonth)).count();

        BigDecimal revenueThisMonth = userIds.isEmpty()
                ? BigDecimal.ZERO
                : nz(paymentRepository.sumSpendForUsersInPeriod(userIds, PaymentStatus.COMPLETED, startThisMonth, startNextMonth));

        CasProfileResponse.Acquisition acquisition =
                new CasProfileResponse.Acquisition(totalAcquired, newThisMonth, newLastMonth);
        CasProfileResponse.Revenue revenue = new CasProfileResponse.Revenue(
                revenueLifetime, revenueThisMonth,
                earnings(revenueLifetime, rate), earnings(revenueThisMonth, rate));

        BigDecimal avgCustomerSpendPerMonth = totalAcquired == 0
                ? BigDecimal.ZERO
                : revenueLifetime
                    .divide(BigDecimal.valueOf(totalAcquired), 4, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(avgTenureMonths), 2, RoundingMode.HALF_UP);
        Double avgOrderFrequency = totalAcquired == 0 ? null
                : round1((double) totalOrders / totalAcquired / avgTenureMonths);
        Double activeRate = totalAcquired == 0 ? null : round1((double) active / totalAcquired * 100);
        Double repeatRate = totalAcquired == 0 ? null : round1((double) repeat / totalAcquired * 100);
        CasProfileResponse.Quality quality =
                new CasProfileResponse.Quality(avgCustomerSpendPerMonth, avgOrderFrequency, activeRate, repeatRate);

        long remaining = Math.max(0, milestoneTarget - repeat);
        long monthsSinceStart = r.getCreatedAt() == null ? 1 : Math.max(1, ChronoUnit.MONTHS.between(r.getCreatedAt(), now));
        double repeatPerMonth = (double) repeat / monthsSinceStart;
        Double estMonths = repeat >= milestoneTarget ? 0.0 : (repeatPerMonth > 0 ? round1(remaining / repeatPerMonth) : null);
        boolean onSalary = new HashSet<>(ruleRepository.findReferrerIdsWithActiveRuleType(ReferralRuleType.MANUAL_OVERRIDE))
                .contains(referrerId);
        CasProfileResponse.Milestone milestone = new CasProfileResponse.Milestone(
                repeat, milestoneTarget, remaining, estMonths, milestoneStatus(repeat, milestoneTarget, onSalary));

        Map<String, Long> byMonth = new TreeMap<>();
        for (ReferralAttribution a : attributions) {
            if (a.getRegisteredAt() != null) {
                byMonth.merge(YearMonth.from(a.getRegisteredAt()).toString(), 1L, Long::sum);
            }
        }
        List<CasProfileResponse.AcquiredByMonth> acquiredByMonth = byMonth.entrySet().stream()
                .map(e -> new CasProfileResponse.AcquiredByMonth(e.getKey(), e.getValue())).toList();

        List<CasProfileResponse.CasCustomer> customers = new ArrayList<>();
        int idx = 0;
        for (ReferralAttribution a : attributions) {
            idx++;
            String uid = a.getUserId();
            LocalDateTime last = lastOrder.get(uid);
            customers.add(new CasProfileResponse.CasCustomer(
                    "Customer #" + idx,
                    a.getRegisteredAt(),
                    orderCount.getOrDefault(uid, 0L),
                    last,
                    spend.getOrDefault(uid, BigDecimal.ZERO),
                    CustomerIntelligenceService.recencyStatus(last, now)));
        }

        return new CasProfileResponse(r.getId(), r.getName(), r.getReferralCode(),
                acquisition, revenue, quality, milestone, acquiredByMonth, customers);
    }

    private boolean inPeriod(LocalDateTime t, LocalDateTime start, LocalDateTime end) {
        return t != null && !t.isBefore(start) && t.isBefore(end);
    }

    private String milestoneStatus(long repeat, int target, boolean onSalary) {
        if (repeat >= target) return onSalary ? "ACTIVE" : "ELIGIBLE";
        if (repeat >= (long) Math.ceil(target * 0.8)) return "NEAR_MILESTONE";
        if (repeat > 0) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private BigDecimal earnings(BigDecimal revenue, BigDecimal rate) {
        return revenue.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private Double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Comparator<CasListItemResponse> comparator(String sort, String direction) {
        String field = sort == null ? "revenueGenerated" : sort;
        Comparator<CasListItemResponse> c = switch (field) {
            case "customersAcquired" -> Comparator.comparingLong(CasListItemResponse::customersAcquired);
            case "activeCustomers" -> Comparator.comparingLong(CasListItemResponse::activeCustomers);
            case "repeatCustomers" -> Comparator.comparingLong(CasListItemResponse::repeatCustomers);
            case "retentionRate" -> Comparator.comparing(x -> x.retentionRate() == null ? 0.0 : x.retentionRate());
            case "name" -> Comparator.comparing(x -> x.name() == null ? "" : x.name().toLowerCase());
            default -> Comparator.comparing(CasListItemResponse::revenueGenerated,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        boolean desc = direction == null || direction.isBlank() || direction.equalsIgnoreCase("desc");
        return desc ? c.reversed() : c;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.BookingLaundryItem;
import com.work.mautonlaundry.data.model.ReferralAttribution;
import com.work.mautonlaundry.data.model.Referrer;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.data.repository.ReferralAttributionRepository;
import com.work.mautonlaundry.data.repository.ReferrerRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.analytics.CustomerListItemResponse;
import com.work.mautonlaundry.dtos.responses.analytics.CustomerProfileResponse;
import com.work.mautonlaundry.dtos.responses.analytics.LeaderboardEntry;
import com.work.mautonlaundry.dtos.responses.analytics.MoneyComparison;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Customer Intelligence list view. Aggregates per-customer metrics from a few
 * batch queries, then assembles/filters/sorts/paginates in memory — appropriate
 * and correct at launch scale (thousands of customers), per the spec.
 */
@Service
@RequiredArgsConstructor
public class CustomerIntelligenceService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReferralAttributionRepository referralAttributionRepository;
    private final ReferrerRepository referrerRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final PricingEngine pricingEngine;

    @Transactional(readOnly = true)
    public Page<CustomerListItemResponse> listCustomers(String status,
                                                        String acquisitionSource,
                                                        LocalDate registeredFrom,
                                                        LocalDate registeredTo,
                                                        BigDecimal minSpend,
                                                        String search,
                                                        String sort,
                                                        String direction,
                                                        int page,
                                                        int size) {
        BigDecimal rate = pricingEngine.getImototoCommissionRate();
        LocalDateTime now = LocalDateTime.now();

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
        Map<String, String> acquisition = new HashMap<>();
        for (Object[] row : referralAttributionRepository.acquisitionSourceByUser()) {
            acquisition.put((String) row[0], row[1] == null ? "Organic" : row[1].toString());
        }

        List<CustomerListItemResponse> items = new ArrayList<>();
        for (AppUser u : userRepository.findCustomersWithOrders()) {
            String id = u.getId();
            BigDecimal lifetime = spend.getOrDefault(id, BigDecimal.ZERO);
            LocalDateTime last = lastOrder.get(id);
            String source = acquisition.getOrDefault(id, "Organic");
            String rawPhone = u.getPhone_number();

            CustomerListItemResponse item = new CustomerListItemResponse(
                    id,
                    u.getFull_name(),
                    maskPhone(rawPhone),
                    cityOf(u),
                    u.getCreatedAt(),
                    orderCount.getOrDefault(id, 0L),
                    lifetime,
                    lifetime.multiply(rate).setScale(2, RoundingMode.HALF_UP),
                    last,
                    recencyStatus(last, now),
                    source);

            // ---- filters ----
            if (registeredFrom != null && (u.getCreatedAt() == null
                    || u.getCreatedAt().toLocalDate().isBefore(registeredFrom))) continue;
            if (registeredTo != null && (u.getCreatedAt() == null
                    || u.getCreatedAt().toLocalDate().isAfter(registeredTo))) continue;
            if (minSpend != null && lifetime.compareTo(minSpend) < 0) continue;
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(item.status())) continue;
            if (acquisitionSource != null && !acquisitionSource.isBlank()
                    && !acquisitionSource.equalsIgnoreCase(source)) continue;
            if (search != null && !search.isBlank()) {
                String term = search.toLowerCase();
                boolean matches = (u.getFull_name() != null && u.getFull_name().toLowerCase().contains(term))
                        || (rawPhone != null && rawPhone.toLowerCase().contains(term));
                if (!matches) continue;
            }
            items.add(item);
        }

        items.sort(comparator(sort, direction));

        int total = items.size();
        int safeSize = size <= 0 ? 20 : size;
        int from = Math.max(0, page) * safeSize;
        int to = Math.min(from + safeSize, total);
        List<CustomerListItemResponse> pageContent = from >= total ? List.of() : items.subList(from, to);

        return new PageImpl<>(pageContent, PageRequest.of(Math.max(0, page), safeSize), total);
    }

    private Comparator<CustomerListItemResponse> comparator(String sort, String direction) {
        String field = sort == null ? "lifetimeSpend" : sort;
        Comparator<CustomerListItemResponse> c = switch (field) {
            case "totalOrders" -> Comparator.comparingLong(CustomerListItemResponse::totalOrders);
            case "customerSince" -> Comparator.comparing(CustomerListItemResponse::customerSince,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "lastOrderDate" -> Comparator.comparing(CustomerListItemResponse::lastOrderDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "name" -> Comparator.comparing(c2 -> c2.name() == null ? "" : c2.name().toLowerCase());
            default -> Comparator.comparing(CustomerListItemResponse::lifetimeSpend,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        // Default direction is DESC (highest lifetime spend first).
        boolean desc = direction == null || direction.isBlank()
                ? true
                : direction.equalsIgnoreCase("desc");
        return desc ? c.reversed() : c;
    }

    private String cityOf(AppUser u) {
        Address addr = u.getDefaultAddress();
        return addr == null ? null : addr.getCity();
    }

    /**
     * Customer value leaderboard for one metric, top {@code limit}.
     * Metrics: LIFETIME_SPEND, ORDERS_THIS_MONTH, AVG_MONTHLY_SPEND, PLATFORM_EARNINGS.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(String metric, int limit) {
        int n = limit <= 0 ? 10 : Math.min(limit, 50);
        String m = metric == null ? "LIFETIME_SPEND" : metric.toUpperCase();
        BigDecimal rate = pricingEngine.getImototoCommissionRate();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDateTime startThisMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        Map<String, BigDecimal> spend = new HashMap<>();
        for (Object[] row : paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)) {
            spend.put((String) row[0], toBigDecimal(row[1]));
        }
        Map<String, Long> ordersThisMonth = new HashMap<>();
        if ("ORDERS_THIS_MONTH".equals(m)) {
            for (Object[] row : bookingRepository.aggregateOrdersPerCustomerInPeriod(startThisMonth, startNextMonth)) {
                ordersThisMonth.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
            }
        }

        record Scored(AppUser u, BigDecimal value) {}
        List<Scored> scored = new ArrayList<>();
        for (AppUser u : userRepository.findCustomersWithOrders()) {
            BigDecimal lifetime = spend.getOrDefault(u.getId(), BigDecimal.ZERO);
            BigDecimal value = switch (m) {
                case "ORDERS_THIS_MONTH" -> BigDecimal.valueOf(ordersThisMonth.getOrDefault(u.getId(), 0L));
                case "AVG_MONTHLY_SPEND" -> {
                    long months = u.getCreatedAt() == null ? 1 : Math.max(1, ChronoUnit.MONTHS.between(u.getCreatedAt(), now));
                    yield lifetime.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
                }
                case "PLATFORM_EARNINGS" -> lifetime.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                default -> lifetime; // LIFETIME_SPEND
            };
            scored.add(new Scored(u, value));
        }
        scored.sort(Comparator.comparing(Scored::value, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 0;
        for (Scored s : scored) {
            if (rank >= n) break;
            rank++;
            result.add(new LeaderboardEntry(rank, s.u().getId(), s.u().getFull_name(), s.value()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerProfile(String userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));

        BigDecimal rate = pricingEngine.getImototoCommissionRate();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDateTime startThisMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        LocalDateTime startLastMonth = today.withDayOfMonth(1).minusMonths(1).atStartOfDay();

        List<Booking> bookings = new ArrayList<>(bookingRepository.findByUserAndDeletedFalse(user));
        bookings.sort(Comparator.comparing(Booking::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        long totalOrders = bookings.size();

        // ---- Revenue ----
        BigDecimal lifetimeSpend = nz(paymentRepository.sumSpendForUser(userId, PaymentStatus.COMPLETED));
        BigDecimal spendThisMonth = nz(paymentRepository.sumSpendForUserInPeriod(userId, PaymentStatus.COMPLETED, startThisMonth, startNextMonth));
        BigDecimal spendLastMonth = nz(paymentRepository.sumSpendForUserInPeriod(userId, PaymentStatus.COMPLETED, startLastMonth, startThisMonth));
        long monthsSinceReg = user.getCreatedAt() == null
                ? 1 : Math.max(1, ChronoUnit.MONTHS.between(user.getCreatedAt(), now));
        BigDecimal avgMonthlySpend = lifetimeSpend.divide(BigDecimal.valueOf(monthsSinceReg), 2, RoundingMode.HALF_UP);
        BigDecimal avgOrderValue = totalOrders == 0
                ? BigDecimal.ZERO
                : lifetimeSpend.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        CustomerProfileResponse.Revenue revenue = new CustomerProfileResponse.Revenue(
                lifetimeSpend,
                lifetimeSpend.multiply(rate).setScale(2, RoundingMode.HALF_UP),
                avgMonthlySpend,
                avgOrderValue,
                MoneyComparison.of(spendThisMonth, spendLastMonth));

        // ---- Behaviour ----
        List<LocalDateTime> dates = bookings.stream().map(Booking::getCreatedAt)
                .filter(java.util.Objects::nonNull).toList();
        LocalDateTime lastOrder = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        Long daysSinceLast = lastOrder == null ? null : ChronoUnit.DAYS.between(lastOrder, now);
        long ordersThisMonth = dates.stream()
                .filter(d -> !d.isBefore(startThisMonth) && d.isBefore(startNextMonth)).count();
        long ordersLastMonth = dates.stream()
                .filter(d -> !d.isBefore(startLastMonth) && d.isBefore(startThisMonth)).count();

        Double avgGap = null;
        Long longestGap = null;
        if (dates.size() >= 2) {
            long totalGap = 0;
            long maxGap = 0;
            for (int i = 1; i < dates.size(); i++) {
                long gap = ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
                totalGap += gap;
                maxGap = Math.max(maxGap, gap);
            }
            avgGap = BigDecimal.valueOf((double) totalGap / (dates.size() - 1))
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            longestGap = maxGap;
        }
        CustomerProfileResponse.Behaviour behaviour = new CustomerProfileResponse.Behaviour(
                totalOrders, ordersThisMonth, ordersLastMonth, daysSinceLast, avgGap, longestGap);

        // ---- Identity / acquisition ----
        String acquisitionSource = "Organic";
        String referralCode = null;
        ReferralAttribution attribution = referralAttributionRepository.findByUserId(userId).orElse(null);
        if (attribution != null) {
            referralCode = attribution.getReferralCodeUsed();
            Referrer referrer = referrerRepository.findById(attribution.getReferrerId()).orElse(null);
            if (referrer != null) acquisitionSource = referrer.getName();
        }
        Address addr = user.getDefaultAddress();
        long accountAgeDays = user.getCreatedAt() == null ? 0 : ChronoUnit.DAYS.between(user.getCreatedAt(), now);
        CustomerProfileResponse.Identity identity = new CustomerProfileResponse.Identity(
                user.getFull_name(),
                maskEmail(user.getEmail()),
                maskPhone(user.getPhone_number()),
                addr == null ? null : addr.getCity(),
                addr == null ? null : addr.getState(),
                user.getCreatedAt(),
                accountAgeDays,
                acquisitionSource,
                referralCode);

        // ---- Status classification ----
        String status = profileStatus(daysSinceLast, ordersThisMonth, ordersLastMonth);

        // ---- Order history (most recent first) ----
        Map<String, String> laundrymanByBooking = new HashMap<>();
        for (Object[] row : laundrymanAssignmentRepository.findLaundrymanNamesByUser(userId)) {
            laundrymanByBooking.putIfAbsent((String) row[0], row[1] == null ? null : row[1].toString());
        }
        List<CustomerProfileResponse.OrderHistoryItem> history = new ArrayList<>();
        for (int i = bookings.size() - 1; i >= 0; i--) { // dates sorted asc -> iterate desc
            Booking b = bookings.get(i);
            BigDecimal total = b.getFinalAmount() != null ? b.getFinalAmount() : b.getTotalPrice();
            List<String> items = b.getItems() == null ? List.of()
                    : b.getItems().stream().map(this::describeItem).toList();
            history.add(new CustomerProfileResponse.OrderHistoryItem(
                    b.getId(), b.getCreatedAt(),
                    b.getStatus() == null ? null : b.getStatus().name(),
                    total, laundrymanByBooking.get(b.getId()), items));
        }

        return new CustomerProfileResponse(identity, revenue, behaviour, status, history);
    }

    private String describeItem(BookingLaundryItem item) {
        return item.getQuantity() + "× " + item.getItemType();
    }

    private String profileStatus(Long daysSinceLast, long ordersThisMonth, long ordersLastMonth) {
        if (daysSinceLast == null) return "HIGH_CHURN_RISK";
        if (daysSinceLast > 90) return "HIGH_CHURN_RISK";
        if (daysSinceLast >= 45) return "AT_RISK";
        if (ordersThisMonth < ordersLastMonth) return "WATCHLIST"; // frequency dropped but still ordering
        return "HEALTHY";
    }

    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String maskPhone(String phone) {
        if (phone == null) return null;
        String d = phone.trim();
        if (d.length() <= 6) return "***";
        return d.substring(0, 4) + "****" + d.substring(d.length() - 2);
    }

    /** ACTIVE <=30d, INACTIVE 31-60, AT_RISK 61-90, CHURNED >90 (or never ordered). */
    static String recencyStatus(LocalDateTime lastOrder, LocalDateTime now) {
        if (lastOrder == null) return "CHURNED";
        long days = ChronoUnit.DAYS.between(lastOrder, now);
        if (days <= 30) return "ACTIVE";
        if (days <= 60) return "INACTIVE";
        if (days <= 90) return "AT_RISK";
        return "CHURNED";
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}

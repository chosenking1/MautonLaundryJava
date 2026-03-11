package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogRepository auditLogRepository;
    private final PaymentRepository paymentRepository;
    private final RoleRepository roleRepository;
    private final BookingLaundryItemRepository bookingLaundryItemRepository;
    
    public DashboardAnalyticsResponse getDashboardAnalytics(LocalDate from, LocalDate to) {
        DashboardAnalyticsResponse analytics = new DashboardAnalyticsResponse();

        analytics.setTotalUsers(safeLong(userRepository.countByDeletedFalse()));
        analytics.setActiveUsers(safeLong(userRepository.countByDeletedFalseAndOnlineTrue()));

        LocalDateTime startOfToday = LocalDateTime.now().toLocalDate().atStartOfDay();
        analytics.setTotalBookings(safeLong(bookingRepository.countActiveBookings()));
        analytics.setTodayBookings(safeLong(bookingRepository.countByCreatedAtAfter(startOfToday)));

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        analytics.setTotalRevenue(safeBigDecimal(paymentRepository.sumTotalAmountByStatus(PaymentStatus.COMPLETED)));
        analytics.setTodayRevenue(safeBigDecimal(paymentRepository.sumAmountByStatusAndPaymentDateAfter(PaymentStatus.COMPLETED, startOfToday)));
        analytics.setMonthlyRevenue(safeBigDecimal(paymentRepository.sumAmountByStatusAndPaymentDateAfter(PaymentStatus.COMPLETED, startOfMonth)));

        analytics.setTodayLogins(safeLong(auditLogRepository.countByActionAndTimestampAfter("LOGIN", startOfToday)));
        analytics.setRecentActivities(auditLogRepository.findTop10ByOrderByTimestampDesc().stream()
                .map(this::toAuditLogResponse)
                .toList());

        // If time range provided, apply filters
        if (from != null && to != null) {
            LocalDateTime fromDateTime = from.atStartOfDay();
            LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();
            
            analytics.setTotalBookings(safeLong(bookingRepository.countBetween(fromDateTime, toDateTime)));
            analytics.setTotalRevenue(safeBigDecimal(paymentRepository.sumAmountByStatusAndDateBetween(PaymentStatus.COMPLETED, fromDateTime, toDateTime)));
        }

        // New breakdown fields
        analytics.setBookingsByStatus(getBookingsByStatus());
        analytics.setRevenueByPaymentMethod(getRevenueByPaymentMethod());
        analytics.setRevenueByServiceCategory(getRevenueByServiceCategory());
        
        // User behavior metrics
        analytics.setNewUsersToday(safeLong(userRepository.countCreatedAfter(startOfToday)));
        analytics.setReturningUsersToday(safeLong(userRepository.countUsersWithBookingsAfter(startOfToday.minusDays(7))));
        analytics.setRetention7d(calculateRetention7d());
        
        // Operational metrics
        analytics.setOnTimeDeliveryRate(calculateOnTimeDeliveryRate());
        analytics.setAvgTurnaroundHours(calculateAvgTurnaroundHours());
        analytics.setFailedPayments(safeLong(paymentRepository.countByStatus(PaymentStatus.FAILED)));
        analytics.setRefundedPayments(safeLong(paymentRepository.countByStatus(PaymentStatus.REFUNDED)));

        return analytics;
    }

    private List<BookingsByStatus> getBookingsByStatus() {
        List<Object[]> results = bookingRepository.countGroupByStatus();
        List<BookingsByStatus> list = new ArrayList<>();
        for (Object[] row : results) {
            BookingsByStatus item = new BookingsByStatus();
            item.setStatus(row[0] != null ? row[0].toString() : "UNKNOWN");
            item.setCount(row[1] != null ? (Long) row[1] : 0L);
            list.add(item);
        }
        return list;
    }

    private List<RevenueByPaymentMethod> getRevenueByPaymentMethod() {
        List<Object[]> results = paymentRepository.sumAmountByStatusGroupByPaymentMethod(PaymentStatus.COMPLETED);
        List<RevenueByPaymentMethod> list = new ArrayList<>();
        for (Object[] row : results) {
            RevenueByPaymentMethod item = new RevenueByPaymentMethod();
            item.setMethod(row[0] != null ? row[0].toString() : "UNKNOWN");
            item.setTotal(row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
            list.add(item);
        }
        return list;
    }

    private List<RevenueByServiceCategory> getRevenueByServiceCategory() {
        List<Object[]> results = bookingLaundryItemRepository.sumTotalPriceGroupByCategory();
        List<RevenueByServiceCategory> list = new ArrayList<>();
        for (Object[] row : results) {
            RevenueByServiceCategory item = new RevenueByServiceCategory();
            item.setCategory(row[0] != null ? row[0].toString() : "UNKNOWN");
            item.setTotal(row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
            list.add(item);
        }
        return list;
    }

    private Double calculateRetention7d() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime fourteenDaysAgo = now.minusDays(14);
        
        List<?> usersLast7Days = userRepository.findUsersWithActivityBetween(sevenDaysAgo, now);
        List<?> usersPrev7Days = userRepository.findUsersWithActivityBetween(fourteenDaysAgo, sevenDaysAgo);
        
        if (usersPrev7Days.isEmpty()) {
            return 0.0;
        }
        
        long retained = usersLast7Days.stream()
                .filter(user -> usersPrev7Days.contains(user))
                .count();
        
        return (double) retained / usersPrev7Days.size() * 100;
    }

    private Double calculateOnTimeDeliveryRate() {
        Long onTime = bookingRepository.countOnTimeDeliveries();
        Long completed = bookingRepository.countCompletedDeliveries();
        if (completed == null || completed == 0) {
            return 0.0;
        }
        return (double) onTime / completed * 100;
    }

    private Double calculateAvgTurnaroundHours() {
        Double hours = bookingRepository.avgTurnaroundHours();
        return hours != null ? hours : 0.0;
    }
    
    public UsersByRoleResponse getUsersByRole() {
        List<UsersByRoleResponse.RoleCount> roles = roleRepository.findAll().stream()
                .map(role -> new UsersByRoleResponse.RoleCount(role.getName(), safeLong(userRepository.countByRole(role))))
                .toList();
        return new UsersByRoleResponse(roles);
    }
    
    public MonthlyStatsResponse getMonthlyStats(Integer year, Integer month) {
        LocalDate date = LocalDate.now();
        if (year != null && month != null) {
            date = LocalDate.of(year, month, 1);
        } else {
            year = date.getYear();
            month = date.getMonthValue();
            date = date.withDayOfMonth(1);
        }
        
        LocalDateTime startOfMonth = date.atStartOfDay();
        LocalDateTime endOfMonth = date.plusMonths(1).atStartOfDay();

        MonthlyStatsResponse monthlyStats = new MonthlyStatsResponse();
        monthlyStats.setMonthlyBookings(safeLong(bookingRepository.countBetween(startOfMonth, endOfMonth)));
        monthlyStats.setMonthlyRevenue(safeBigDecimal(paymentRepository.sumAmountByStatusAndDateBetween(PaymentStatus.COMPLETED, startOfMonth, endOfMonth)));
        monthlyStats.setMonthlyNewUsers(safeLong(userRepository.countCreatedBetween(startOfMonth, endOfMonth)));
        return monthlyStats;
    }
    
    public TimeSeriesResponse getTimeSeries(String metric, LocalDate from, LocalDate to, String groupBy) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required");
        }
        
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();
        
        List<TimeSeriesPoint> points = new ArrayList<>();
        
        switch (metric.toUpperCase()) {
            case "BOOKINGS" -> points = generateBookingTimeSeries(fromDateTime, toDateTime, groupBy);
            case "REVENUE" -> points = generateRevenueTimeSeries(fromDateTime, toDateTime, groupBy);
            case "NEW_USERS" -> points = generateNewUsersTimeSeries(fromDateTime, toDateTime, groupBy);
            case "ACTIVE_USERS" -> points = generateActiveUsersTimeSeries(fromDateTime, toDateTime, groupBy);
            default -> throw new IllegalArgumentException("Invalid metric: " + metric);
        }
        
        TimeSeriesResponse response = new TimeSeriesResponse();
        response.setMetric(metric.toUpperCase());
        response.setGroupBy(groupBy.toUpperCase());
        response.setPoints(points);
        return response;
    }
    
    private List<TimeSeriesPoint> generateBookingTimeSeries(LocalDateTime from, LocalDateTime to, String groupBy) {
        List<TimeSeriesPoint> points = new ArrayList<>();
        LocalDate current = from.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        while (current.isBefore(to.toLocalDate())) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            Long count = bookingRepository.countBetween(dayStart, dayEnd);
            TimeSeriesPoint point = new TimeSeriesPoint();
            point.setTimestamp(current.format(formatter));
            point.setValue(count != null ? count : 0);
            points.add(point);
            current = current.plusDays(1);
        }
        return points;
    }
    
    private List<TimeSeriesPoint> generateRevenueTimeSeries(LocalDateTime from, LocalDateTime to, String groupBy) {
        List<TimeSeriesPoint> points = new ArrayList<>();
        LocalDate current = from.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        while (current.isBefore(to.toLocalDate())) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            BigDecimal revenue = paymentRepository.sumAmountByStatusAndDateBetween(PaymentStatus.COMPLETED, dayStart, dayEnd);
            TimeSeriesPoint point = new TimeSeriesPoint();
            point.setTimestamp(current.format(formatter));
            point.setValue(revenue != null ? revenue : BigDecimal.ZERO);
            points.add(point);
            current = current.plusDays(1);
        }
        return points;
    }
    
    private List<TimeSeriesPoint> generateNewUsersTimeSeries(LocalDateTime from, LocalDateTime to, String groupBy) {
        List<TimeSeriesPoint> points = new ArrayList<>();
        LocalDate current = from.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        while (current.isBefore(to.toLocalDate())) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            Long count = userRepository.countCreatedBetween(dayStart, dayEnd);
            TimeSeriesPoint point = new TimeSeriesPoint();
            point.setTimestamp(current.format(formatter));
            point.setValue(count != null ? count : 0);
            points.add(point);
            current = current.plusDays(1);
        }
        return points;
    }
    
    private List<TimeSeriesPoint> generateActiveUsersTimeSeries(LocalDateTime from, LocalDateTime to, String groupBy) {
        List<TimeSeriesPoint> points = new ArrayList<>();
        LocalDate current = from.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        while (current.isBefore(to.toLocalDate())) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            Long count = userRepository.countUsersWithBookingsBetween(dayStart, dayEnd);
            TimeSeriesPoint point = new TimeSeriesPoint();
            point.setTimestamp(current.format(formatter));
            point.setValue(count != null ? count : 0);
            points.add(point);
            current = current.plusDays(1);
        }
        return points;
    }
    
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toAuditLogResponse);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private AuditLogResponse toAuditLogResponse(com.work.mautonlaundry.data.model.AuditLog auditLog) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(auditLog.getId());
        response.setAction(auditLog.getAction());
        response.setResource(auditLog.getResource());
        response.setResourceId(auditLog.getResourceId());
        response.setTimestamp(auditLog.getTimestamp());
        response.setDetails(auditLog.getDetails());
        response.setUserEmail(auditLog.getUserEmail());
        return response;
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.DashboardAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the active-user-monitor fix: "active users" must be derived from
 * order recency (customers who ordered in the last ~30 days), NOT from the
 * stale {@code users.online} boolean that defaulted TRUE and was never reset
 * for customers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsActiveUsersTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BookingLaundryItemRepository bookingLaundryItemRepository;

    @InjectMocks private AnalyticsService analyticsService;

    @Test
    void activeUsersComesFromLast30DayOrderRecency_notOnlineFlag() {
        // List-returning calls must not be null (the service streams over them).
        when(auditLogRepository.findTop10ByOrderByTimestampDesc()).thenReturn(List.of());
        when(bookingRepository.countGroupByStatus()).thenReturn(List.of());
        when(paymentRepository.sumAmountByStatusGroupByPaymentMethod(any())).thenReturn(List.of());
        when(bookingLaundryItemRepository.sumTotalPriceGroupByCategory()).thenReturn(List.of());
        when(userRepository.findUsersWithActivityBetween(any(), any())).thenReturn(List.of());

        // The recency query returns the active-user count.
        when(userRepository.countUsersWithBookingsAfter(any())).thenReturn(3L);

        DashboardAnalyticsResponse result = analyticsService.getDashboardAnalytics(null, null);

        // 1) Active users is the recency count, not "everyone".
        assertThat(result.getActiveUsers()).isEqualTo(3L);

        // 2) The buggy online-flag query is never used anymore.
        verify(userRepository, never()).countByDeletedFalseAndOnlineTrue();

        // 3) The recency window is ~30 days ago (within a minute of now-30d).
        ArgumentCaptor<LocalDateTime> windowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository, atLeastOnce()).countUsersWithBookingsAfter(windowCaptor.capture());
        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        boolean has30DayWindow = windowCaptor.getAllValues().stream()
                .anyMatch(d -> Math.abs(java.time.Duration.between(d, expected).toMinutes()) < 1);
        assertThat(has30DayWindow)
                .as("active-users window should be ~30 days ago")
                .isTrue();
    }
}

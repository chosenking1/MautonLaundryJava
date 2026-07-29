package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AnalyticsDailySnapshot;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.AnalyticsDailySnapshotRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsSnapshotServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingEngine pricingEngine;
    @Mock private AnalyticsDailySnapshotRepository snapshotRepository;

    @InjectMocks private AnalyticsSnapshotService service;

    @Test
    void computesDailySnapshotWithConfigurableEarningsAndAov() {
        LocalDate date = LocalDate.of(2026, 5, 31);

        when(paymentRepository.sumAmountByStatusAndDateBetween(eq(PaymentStatus.COMPLETED), any(), any()))
                .thenReturn(new BigDecimal("1000"));
        when(bookingRepository.countBetween(any(), any())).thenReturn(5L);
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.countFirstTimeCustomersBetween(any(), any())).thenReturn(2L);
        when(userRepository.countReturningCustomersBetween(any(), any())).thenReturn(1L);
        when(userRepository.countUsersWithBookingsBetween(any(), any())).thenReturn(4L);
        when(snapshotRepository.findById(date)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AnalyticsDailySnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AnalyticsDailySnapshot snap = service.generateSnapshot(date);

        assertThat(snap.getSnapshotDate()).isEqualTo(date);
        assertThat(snap.getTotalRevenue()).isEqualByComparingTo("1000");
        assertThat(snap.getPlatformEarnings()).isEqualByComparingTo("300.00"); // 1000 x 0.30
        assertThat(snap.getTotalOrders()).isEqualTo(5);
        assertThat(snap.getNewCustomers()).isEqualTo(2);
        assertThat(snap.getReturningCustomers()).isEqualTo(1);
        assertThat(snap.getActiveCustomers()).isEqualTo(4);
        assertThat(snap.getAverageOrderValue()).isEqualByComparingTo("200.00"); // 1000 / 5
    }

    @Test
    void zeroOrdersYieldsZeroAovAndEarnings() {
        LocalDate date = LocalDate.of(2026, 5, 31);
        when(paymentRepository.sumAmountByStatusAndDateBetween(eq(PaymentStatus.COMPLETED), any(), any()))
                .thenReturn(null); // no payments
        when(bookingRepository.countBetween(any(), any())).thenReturn(0L);
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.countFirstTimeCustomersBetween(any(), any())).thenReturn(0L);
        when(userRepository.countReturningCustomersBetween(any(), any())).thenReturn(0L);
        when(userRepository.countUsersWithBookingsBetween(any(), any())).thenReturn(0L);
        when(snapshotRepository.findById(date)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AnalyticsDailySnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        AnalyticsDailySnapshot snap = service.generateSnapshot(date);

        assertThat(snap.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(snap.getPlatformEarnings()).isEqualByComparingTo("0.00");
        assertThat(snap.getAverageOrderValue()).isEqualByComparingTo("0");
        assertThat(snap.getTotalOrders()).isZero();
    }
}

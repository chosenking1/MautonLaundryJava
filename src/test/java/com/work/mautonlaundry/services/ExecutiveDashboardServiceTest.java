package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.ExecutiveDashboardResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutiveDashboardServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private ExecutiveDashboardService service;

    @Test
    void buildsAllSectionsWithConfigurableEarningsAndDerivedMetrics() {
        // Every revenue period returns 1000 -> comparisons are flat (0% change).
        when(paymentRepository.sumRevenueInPeriod(any(), any(), any())).thenReturn(new BigDecimal("1000"));
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(bookingRepository.countBetween(any(), any())).thenReturn(5L);

        when(userRepository.countDistinctCustomers()).thenReturn(50L);
        when(userRepository.countUsersWithBookingsAfter(any())).thenReturn(20L);
        when(userRepository.countFirstTimeCustomersBetween(any(), any())).thenReturn(3L);
        when(userRepository.countReturningCustomersBetween(any(), any())).thenReturn(4L);
        when(userRepository.countInactiveCustomers(any())).thenReturn(7L);

        when(laundrymanAssignmentRepository.countActiveLaundrymenSince(any())).thenReturn(2L);
        when(referralAttributionRepository.countActiveReferrersWithType(any(ReferrerType.class), any()))
                .thenReturn(1L);

        when(paymentRepository.topCustomersByLifetimeSpend(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"u1", "Alice", new BigDecimal("500")}));
        when(paymentRepository.topCustomersBySpendInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.topCasByReferredRevenueInPeriod(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ExecutiveDashboardResponse dash = service.getExecutiveDashboard();

        // Revenue + earnings (configurable 30%)
        assertThat(dash.revenue().revenueToday().current()).isEqualByComparingTo("1000");
        assertThat(dash.revenue().revenueToday().changePercent()).isEqualTo(0.0); // 1000 vs 1000
        assertThat(dash.revenue().platformEarningsToday()).isEqualByComparingTo("300.00");
        assertThat(dash.revenue().platformEarningsThisMonth().current()).isEqualByComparingTo("300.00");
        assertThat(dash.revenue().revenueThisYear()).isEqualByComparingTo("1000");

        // Customers
        assertThat(dash.customers().totalCustomers()).isEqualTo(50);
        assertThat(dash.customers().activeCustomers()).isEqualTo(20);
        assertThat(dash.customers().inactiveCustomers()).isEqualTo(7);

        // Operational
        assertThat(dash.operational().averageOrderValue()).isEqualByComparingTo("200.00"); // 1000 / 5
        assertThat(dash.operational().activeLaundrymen()).isEqualTo(2);
        assertThat(dash.operational().activeCas()).isEqualTo(1);

        // Growth (derived): repeat rate = returning(4) / active(20) * 100 = 20.0
        assertThat(dash.growth().repeatCustomerRate()).isEqualTo(20.0);
        assertThat(dash.growth().revenueGrowthRate()).isEqualTo(0.0);

        // Rankings mapping
        assertThat(dash.rankings().topCustomersByLifetimeSpend()).hasSize(1);
        assertThat(dash.rankings().topCustomersByLifetimeSpend().get(0).id()).isEqualTo("u1");
        assertThat(dash.rankings().topCustomersByLifetimeSpend().get(0).name()).isEqualTo("Alice");
        assertThat(dash.rankings().topCustomersByLifetimeSpend().get(0).value()).isEqualByComparingTo("500");
        assertThat(dash.rankings().topCustomersBySpendThisMonth()).isEmpty();
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.LeaderboardEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerLeaderboardTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private ReferrerRepository referrerRepository;
    @Mock private LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private CustomerIntelligenceService service;

    private AppUser u(String id, String name) {
        AppUser a = new AppUser();
        a.setId(id);
        a.setFull_name(name);
        a.setCreatedAt(LocalDateTime.now().minusMonths(2));
        return a;
    }

    @Test
    void lifetimeSpendLeaderboardSortsDescAndRespectsLimit() {
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.findAllCustomers()).thenReturn(List.of(u("u1", "Ada"), u("u2", "Ben"), u("u3", "Cee")));
        when(paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)).thenReturn(List.of(
                new Object[]{"u1", new BigDecimal("1000")},
                new Object[]{"u2", new BigDecimal("500")},
                new Object[]{"u3", new BigDecimal("2000")}));

        List<LeaderboardEntry> top = service.leaderboard("LIFETIME_SPEND", 2);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).rank()).isEqualTo(1);
        assertThat(top.get(0).userId()).isEqualTo("u3");
        assertThat(top.get(0).value()).isEqualByComparingTo("2000");
        assertThat(top.get(1).rank()).isEqualTo(2);
        assertThat(top.get(1).userId()).isEqualTo("u1");
    }

    @Test
    void ordersThisMonthLeaderboardUsesPeriodAggregate() {
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.findAllCustomers()).thenReturn(List.of(u("u1", "Ada"), u("u2", "Ben"), u("u3", "Cee")));
        when(paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)).thenReturn(List.of());
        when(bookingRepository.aggregateOrdersPerCustomerInPeriod(any(), any())).thenReturn(List.of(
                new Object[]{"u1", 5L},
                new Object[]{"u2", 1L}));

        List<LeaderboardEntry> top = service.leaderboard("ORDERS_THIS_MONTH", 10);

        assertThat(top.get(0).userId()).isEqualTo("u1");
        assertThat(top.get(0).value()).isEqualByComparingTo("5");
        assertThat(top.get(1).userId()).isEqualTo("u2");
        // u3 had no orders this month -> value 0, ranked last
        assertThat(top.get(2).value()).isEqualByComparingTo("0");
    }
}

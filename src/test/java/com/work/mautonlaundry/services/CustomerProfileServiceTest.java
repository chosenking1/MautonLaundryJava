package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.CustomerProfileResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private ReferrerRepository referrerRepository;
    @Mock private LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private CustomerIntelligenceService service;

    private Booking booking(String id, LocalDateTime created) {
        Booking b = new Booking();
        b.setId(id);
        b.setCreatedAt(created);
        b.setStatus(BookingStatus.COMPLETED);
        b.setTotalPrice(new BigDecimal("300"));
        return b;
    }

    @Test
    void buildsProfileWithMaskedPii_gaps_earnings_andHistoryNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        AppUser u = new AppUser();
        u.setId("c1");
        u.setFull_name("Test Customer");
        u.setEmail("joshua@gmail.com");
        u.setPhone_number("08012345678");
        u.setCreatedAt(now.minusMonths(6));

        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.findById("c1")).thenReturn(Optional.of(u));
        when(bookingRepository.findByUserAndDeletedFalse(u)).thenReturn(List.of(
                booking("b1", now.minusDays(90)),
                booking("b2", now.minusDays(30)),
                booking("b3", now.minusDays(3))));
        when(paymentRepository.sumSpendForUser("c1", PaymentStatus.COMPLETED)).thenReturn(new BigDecimal("900"));
        when(paymentRepository.sumSpendForUserInPeriod(eq("c1"), eq(PaymentStatus.COMPLETED), any(), any()))
                .thenReturn(new BigDecimal("300"));
        when(referralAttributionRepository.findByUserId("c1")).thenReturn(Optional.empty());
        when(laundrymanAssignmentRepository.findLaundrymanNamesByUser("c1")).thenReturn(List.of());

        CustomerProfileResponse p = service.getCustomerProfile("c1");

        // Identity masking + organic
        assertThat(p.identity().email()).isEqualTo("jo***@gmail.com");
        assertThat(p.identity().phone()).isEqualTo("0801****78");
        assertThat(p.identity().acquisitionSource()).isEqualTo("Organic");
        assertThat(p.identity().referralCode()).isNull();

        // Revenue
        assertThat(p.revenue().lifetimeSpend()).isEqualByComparingTo("900");
        assertThat(p.revenue().lifetimePlatformEarnings()).isEqualByComparingTo("270.00"); // 900 x 0.30
        assertThat(p.revenue().averageOrderValue()).isEqualByComparingTo("300.00");        // 900 / 3
        assertThat(p.revenue().averageMonthlySpend()).isEqualByComparingTo("150.00");      // 900 / 6 months

        // Behaviour: gaps 60 (90->30) and 27 (30->3) -> avg 43.5, longest 60; last order 3 days ago
        assertThat(p.behaviour().totalOrders()).isEqualTo(3);
        assertThat(p.behaviour().daysSinceLastOrder()).isEqualTo(3L);
        assertThat(p.behaviour().averageDaysBetweenOrders()).isEqualTo(43.5);
        assertThat(p.behaviour().longestGapDays()).isEqualTo(60L);

        // Status: ordered 3 days ago -> a healthy band (HEALTHY or WATCHLIST), not at-risk/churn
        assertThat(p.status()).isIn("HEALTHY", "WATCHLIST");

        // Order history newest-first
        assertThat(p.orderHistory()).extracting(CustomerProfileResponse.OrderHistoryItem::bookingId)
                .containsExactly("b3", "b2", "b1");
    }

    @Test
    void churnedWhenNoOrderInOver90Days() {
        LocalDateTime now = LocalDateTime.now();
        AppUser u = new AppUser();
        u.setId("c2");
        u.setEmail("a@b.com");
        u.setCreatedAt(now.minusMonths(12));

        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.findById("c2")).thenReturn(Optional.of(u));
        when(bookingRepository.findByUserAndDeletedFalse(u)).thenReturn(List.of(booking("b9", now.minusDays(200))));
        when(paymentRepository.sumSpendForUser("c2", PaymentStatus.COMPLETED)).thenReturn(new BigDecimal("100"));
        when(paymentRepository.sumSpendForUserInPeriod(eq("c2"), eq(PaymentStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(referralAttributionRepository.findByUserId("c2")).thenReturn(Optional.empty());
        when(laundrymanAssignmentRepository.findLaundrymanNamesByUser("c2")).thenReturn(List.of());

        CustomerProfileResponse p = service.getCustomerProfile("c2");
        assertThat(p.status()).isEqualTo("HIGH_CHURN_RISK");
        assertThat(p.behaviour().averageDaysBetweenOrders()).isNull(); // only 1 order
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.data.repository.ReferralAttributionRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.analytics.CustomerListItemResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerIntelligenceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private CustomerIntelligenceService service;

    private AppUser user(String id, String name, String phone, LocalDateTime createdAt) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setFull_name(name);
        u.setPhone_number(phone);
        u.setCreatedAt(createdAt);
        return u;
    }

    private void wire() {
        LocalDateTime now = LocalDateTime.now();
        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(userRepository.findAllCustomers()).thenReturn(List.of(
                user("u1", "Ada Spend", "08012345678", now.minusDays(200)),
                user("u2", "Ben Churn", "07099998888", now.minusDays(300))));
        when(bookingRepository.aggregateOrdersPerCustomer()).thenReturn(List.of(
                new Object[]{"u1", 3L, now.minusDays(5)},
                new Object[]{"u2", 1L, now.minusDays(100)}));
        when(paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)).thenReturn(List.of(
                new Object[]{"u1", new BigDecimal("1000")},
                new Object[]{"u2", new BigDecimal("200")}));
        when(referralAttributionRepository.acquisitionSourceByUser()).thenReturn(List.<Object[]>of(
                new Object[]{"u1", "Simbiat"}));
    }

    @Test
    void assemblesMetricsSortsByLifetimeSpendDescAndMasksPhone() {
        wire();
        Page<CustomerListItemResponse> page =
                service.listCustomers(null, null, null, null, null, null, "lifetimeSpend", "desc", 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        CustomerListItemResponse first = page.getContent().get(0);
        assertThat(first.userId()).isEqualTo("u1");               // 1000 > 200 -> first
        assertThat(first.status()).isEqualTo("ACTIVE");           // ordered 5 days ago
        assertThat(first.lifetimeSpend()).isEqualByComparingTo("1000");
        assertThat(first.platformEarnings()).isEqualByComparingTo("300.00"); // 1000 x 0.30
        assertThat(first.acquisitionSource()).isEqualTo("Simbiat");
        assertThat(first.phone()).isEqualTo("0801****78");        // first 4 + last 2
        assertThat(first.totalOrders()).isEqualTo(3);

        CustomerListItemResponse second = page.getContent().get(1);
        assertThat(second.userId()).isEqualTo("u2");
        assertThat(second.status()).isEqualTo("CHURNED");         // ordered 100 days ago
        assertThat(second.acquisitionSource()).isEqualTo("Organic"); // not referred
    }

    @Test
    void filtersByStatusAndMinSpend() {
        wire();
        // Only CHURNED
        Page<CustomerListItemResponse> churned =
                service.listCustomers("CHURNED", null, null, null, null, null, "lifetimeSpend", "desc", 0, 20);
        assertThat(churned.getContent()).extracting(CustomerListItemResponse::userId).containsExactly("u2");

        // Min spend 500 -> only u1
        Page<CustomerListItemResponse> bigSpenders =
                service.listCustomers(null, null, null, null, new BigDecimal("500"), null, "lifetimeSpend", "desc", 0, 20);
        assertThat(bigSpenders.getContent()).extracting(CustomerListItemResponse::userId).containsExactly("u1");
    }
}

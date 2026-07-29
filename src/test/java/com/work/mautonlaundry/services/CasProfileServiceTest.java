package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ReferralAttribution;
import com.work.mautonlaundry.data.model.Referrer;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.CasProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

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
class CasProfileServiceTest {

    @Mock private ReferrerRepository referrerRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private ReferralPaymentRuleRepository ruleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private CasPerformanceService service;

    @Test
    void buildsProfileWithMilestoneQualityAndAnonymisedCustomers() {
        ReflectionTestUtils.setField(service, "milestoneTarget", 2);
        LocalDateTime now = LocalDateTime.now();

        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(referrerRepository.findById("r1")).thenReturn(Optional.of(
                Referrer.builder().id("r1").name("Simbiat").referralCode("SIM")
                        .referrerType(ReferrerType.SPECIALIST).createdAt(now.minusMonths(10)).build()));
        when(referralAttributionRepository.findByReferrerId("r1")).thenReturn(List.of(
                ReferralAttribution.builder().referrerId("r1").userId("u1").registeredAt(now.minusDays(60)).build(),
                ReferralAttribution.builder().referrerId("r1").userId("u2").registeredAt(now.minusDays(20)).build(),
                ReferralAttribution.builder().referrerId("r1").userId("u3").registeredAt(now.minusDays(5)).build()));
        when(bookingRepository.aggregateOrdersPerCustomer()).thenReturn(List.of(
                new Object[]{"u1", 3L, now.minusDays(5)},
                new Object[]{"u2", 1L, now.minusDays(100)},
                new Object[]{"u3", 2L, now.minusDays(2)}));
        when(paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)).thenReturn(List.of(
                new Object[]{"u1", new BigDecimal("600")},
                new Object[]{"u2", new BigDecimal("100")},
                new Object[]{"u3", new BigDecimal("400")}));
        when(paymentRepository.sumSpendForUsersInPeriod(any(), eq(PaymentStatus.COMPLETED), any(), any()))
                .thenReturn(new BigDecimal("200"));
        when(ruleRepository.findReferrerIdsWithActiveRuleType(ReferralRuleType.MANUAL_OVERRIDE))
                .thenReturn(List.<String>of());

        CasProfileResponse p = service.getCasProfile("r1");

        assertThat(p.acquisition().totalAcquired()).isEqualTo(3);

        assertThat(p.revenue().revenueLifetime()).isEqualByComparingTo("1100"); // 600+100+400
        assertThat(p.revenue().platformEarningsLifetime()).isEqualByComparingTo("330.00"); // 1100 x 0.30
        assertThat(p.revenue().revenueThisMonth()).isEqualByComparingTo("200");
        assertThat(p.revenue().platformEarningsThisMonth()).isEqualByComparingTo("60.00");

        assertThat(p.quality().activeCustomerRate()).isEqualTo(66.7);   // u1,u3 active of 3
        assertThat(p.quality().repeatCustomerRate()).isEqualTo(66.7);   // u1(3),u3(2) of 3

        assertThat(p.milestone().currentRepeat()).isEqualTo(2);
        assertThat(p.milestone().target()).isEqualTo(2);
        assertThat(p.milestone().remaining()).isEqualTo(0);
        assertThat(p.milestone().estimatedMonthsToMilestone()).isEqualTo(0.0);
        assertThat(p.milestone().statusLabel()).isEqualTo("ELIGIBLE"); // repeat>=target, not on salary

        // Anonymised customers, sorted by registration asc -> #1 = earliest (u1)
        assertThat(p.customers()).hasSize(3);
        assertThat(p.customers().get(0).reference()).isEqualTo("Customer #1");
        assertThat(p.customers().get(0).totalOrders()).isEqualTo(3);
        assertThat(p.customers().get(0).status()).isEqualTo("ACTIVE");
        assertThat(p.acquiredByMonth()).isNotEmpty();
    }
}

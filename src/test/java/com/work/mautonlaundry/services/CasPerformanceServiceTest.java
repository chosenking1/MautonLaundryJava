package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ReferralAttribution;
import com.work.mautonlaundry.data.model.Referrer;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.responses.analytics.CasListItemResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CasPerformanceServiceTest {

    @Mock private ReferrerRepository referrerRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private ReferralPaymentRuleRepository ruleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PricingEngine pricingEngine;

    @InjectMocks private CasPerformanceService service;

    private Referrer specialist(String id, String name, String code) {
        return Referrer.builder().id(id).name(name).referralCode(code).referrerType(ReferrerType.SPECIALIST).build();
    }

    private ReferralAttribution attr(String referrerId, String userId) {
        return ReferralAttribution.builder().referrerId(referrerId).userId(userId).build();
    }

    @Test
    void ranksByRevenueAndComputesQualityMetricsAndStatus() {
        ReflectionTestUtils.setField(service, "milestoneTarget", 2);
        LocalDateTime now = LocalDateTime.now();

        when(pricingEngine.getImototoCommissionRate()).thenReturn(new BigDecimal("0.30"));
        when(referrerRepository.findByReferrerType(eq(ReferrerType.SPECIALIST), any()))
                .thenReturn(new PageImpl<>(List.of(
                        specialist("r1", "Simbiat", "SIMBIAT"),
                        specialist("r2", "Victoria", "VICTORIA"))));
        when(referralAttributionRepository.findAll()).thenReturn(List.of(
                attr("r1", "u1"), attr("r1", "u2"), attr("r1", "u3"),
                attr("r2", "u4")));
        when(bookingRepository.aggregateOrdersPerCustomer()).thenReturn(List.of(
                new Object[]{"u1", 2L, now.minusDays(5)},
                new Object[]{"u2", 1L, now.minusDays(100)},
                new Object[]{"u3", 3L, now.minusDays(2)},
                new Object[]{"u4", 1L, now.minusDays(3)}));
        when(paymentRepository.sumSpendPerCustomer(PaymentStatus.COMPLETED)).thenReturn(List.of(
                new Object[]{"u1", new BigDecimal("500")},
                new Object[]{"u2", new BigDecimal("100")},
                new Object[]{"u3", new BigDecimal("1000")},
                new Object[]{"u4", new BigDecimal("200")}));
        when(ruleRepository.findReferrerIdsWithActiveRuleType(ReferralRuleType.MANUAL_OVERRIDE))
                .thenReturn(List.<String>of("r1"));

        List<CasListItemResponse> list = service.listCas("revenueGenerated", "desc", null);

        assertThat(list).hasSize(2);
        CasListItemResponse r1 = list.get(0); // 1600 revenue -> first
        assertThat(r1.referrerId()).isEqualTo("r1");
        assertThat(r1.customersAcquired()).isEqualTo(3);
        assertThat(r1.activeCustomers()).isEqualTo(2);          // u1 (5d), u3 (2d); u2 100d excluded
        assertThat(r1.repeatCustomers()).isEqualTo(2);          // u1 (2), u3 (3)
        assertThat(r1.revenueGenerated()).isEqualByComparingTo("1600");
        assertThat(r1.platformEarnings()).isEqualByComparingTo("480.00"); // 1600 x 0.30
        assertThat(r1.retentionRate()).isEqualTo(66.7);         // 2/3 * 100
        assertThat(r1.status()).isEqualTo("SALARY_ACTIVE");     // repeat>=2 and on MANUAL_OVERRIDE

        CasListItemResponse r2 = list.get(1);
        assertThat(r2.referrerId()).isEqualTo("r2");
        assertThat(r2.repeatCustomers()).isEqualTo(0);
        assertThat(r2.retentionRate()).isEqualTo(100.0);
        assertThat(r2.status()).isEqualTo("COMMISSION_ONLY");
    }
}

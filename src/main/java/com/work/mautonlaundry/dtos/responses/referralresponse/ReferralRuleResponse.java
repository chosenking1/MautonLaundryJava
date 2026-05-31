package com.work.mautonlaundry.dtos.responses.referralresponse;

import com.work.mautonlaundry.data.model.ReferralPaymentRule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReferralRuleResponse(
        String id,
        String referrerId,
        String ruleType,
        String paymentFrequency,
        BigDecimal percentageValue,
        BigDecimal flatFeeAmount,
        Integer bookingNumberFrom,
        Integer bookingNumberTo,
        Integer daysFromRegistrationLimit,
        Integer volumeThreshold,
        Integer milestoneBookingNumber,
        Integer milestonePaymentThreshold,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        boolean active,
        String notes
) {
    public static ReferralRuleResponse from(ReferralPaymentRule r) {
        return new ReferralRuleResponse(
                r.getId(), r.getReferrerId(),
                r.getRuleType().name(), r.getPaymentFrequency().name(),
                r.getPercentageValue(), r.getFlatFeeAmount(),
                r.getBookingNumberFrom(), r.getBookingNumberTo(),
                r.getDaysFromRegistrationLimit(), r.getVolumeThreshold(),
                r.getMilestoneBookingNumber(), r.getMilestonePaymentThreshold(),
                r.getEffectiveFrom(), r.getEffectiveUntil(),
                r.isActive(), r.getNotes());
    }
}

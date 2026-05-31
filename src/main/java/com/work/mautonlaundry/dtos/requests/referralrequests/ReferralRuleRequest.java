package com.work.mautonlaundry.dtos.requests.referralrequests;

import com.work.mautonlaundry.data.model.enums.ReferralPaymentFrequency;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single payment rule. Which fields are required depends on {@code ruleType};
 * validation of type-specific fields is enforced in the service layer.
 */
public record ReferralRuleRequest(
        String id,
        @NotNull ReferralRuleType ruleType,
        @NotNull ReferralPaymentFrequency paymentFrequency,
        BigDecimal percentageValue,
        BigDecimal flatFeeAmount,
        Integer bookingNumberFrom,
        Integer bookingNumberTo,
        Integer daysFromRegistrationLimit,
        Integer volumeThreshold,
        Integer milestoneBookingNumber,
        Integer milestonePaymentThreshold,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String notes
) {
}

package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A recent commission event for the in-app dashboard. Carries no customer PII. */
public record ReferralActivityResponse(
        LocalDateTime date,
        String customerReference,
        String ruleApplied,
        BigDecimal commissionEarned
) {
}

package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row for the pending-payouts dashboard (Screen 4). */
public record PendingPayoutResponse(
        String payoutId,
        String referrerId,
        String name,
        String referrerType,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal amountOwed,
        long daysSinceGenerated
) {
}

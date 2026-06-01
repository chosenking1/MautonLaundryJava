package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Row for the admin referrer list (Screen 1). */
public record ReferrerListItemResponse(
        String id,
        String name,
        String referrerType,
        String referralCode,
        long customersReferred,
        long repeatCustomers,
        BigDecimal pendingPayout,
        LocalDateTime lastPaymentDate,
        boolean active
) {
}

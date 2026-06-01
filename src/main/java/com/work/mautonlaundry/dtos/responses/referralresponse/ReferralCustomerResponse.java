package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An anonymised referred customer. Never exposes real names or emails — the
 * customer is identified only by an opaque "Customer #N" reference.
 */
public record ReferralCustomerResponse(
        String reference,
        LocalDateTime registrationDate,
        long totalBookings,
        LocalDateTime lastBookingDate,
        BigDecimal orderValueGenerated,
        BigDecimal commissionEarned
) {
}

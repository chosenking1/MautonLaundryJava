package com.work.mautonlaundry.dtos.responses.referralresponse;

import java.math.BigDecimal;

/** Referral code plus a pre-written share message for the native share sheet. */
public record ShareDetailsResponse(
        String referralCode,
        String shareMessage,
        String playStoreLink,
        /** Welcome discount the referred customer receives, if a discount is linked; else null. */
        BigDecimal welcomeDiscountValue,
        String welcomeDiscountType
) {
}

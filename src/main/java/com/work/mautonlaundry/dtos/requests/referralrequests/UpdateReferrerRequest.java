package com.work.mautonlaundry.dtos.requests.referralrequests;

import com.work.mautonlaundry.data.model.enums.ReferrerType;

/** Update a referrer's profile fields. Payment rules are managed via the rule endpoints. */
public record UpdateReferrerRequest(
        String name,
        String email,
        String phone,
        ReferrerType referrerType,
        String linkedDiscountId,
        Boolean active,
        String notes
) {
}

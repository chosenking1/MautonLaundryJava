package com.work.mautonlaundry.dtos.requests.referralrequests;

import com.work.mautonlaundry.data.model.enums.ReferrerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateReferrerRequest(
        @NotBlank String name,
        String email,
        String phone,
        @NotNull ReferrerType referrerType,
        /** Optional. Auto-generated from the name if blank. */
        String referralCode,
        /** Optional discount code linked as a welcome incentive for referred customers. */
        String linkedDiscountId,
        String notes,
        /** Optional initial set of payment rules to create alongside the referrer. */
        @Valid List<ReferralRuleRequest> rules
) {
}

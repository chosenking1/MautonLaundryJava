package com.work.mautonlaundry.dtos.responses.referralresponse;

import com.work.mautonlaundry.data.model.Referrer;

import java.time.LocalDateTime;
import java.util.List;

public record ReferrerResponse(
        String id,
        String name,
        String email,
        String phone,
        String referrerType,
        String referralCode,
        String linkedDiscountId,
        boolean active,
        String notes,
        LocalDateTime createdAt,
        List<ReferralRuleResponse> rules
) {
    public static ReferrerResponse from(Referrer r, List<ReferralRuleResponse> rules) {
        return new ReferrerResponse(
                r.getId(), r.getName(), r.getEmail(), r.getPhone(),
                r.getReferrerType().name(), r.getReferralCode(),
                r.getLinkedDiscountId(), r.isActive(), r.getNotes(),
                r.getCreatedAt(), rules);
    }
}

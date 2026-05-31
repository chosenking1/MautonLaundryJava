package com.work.mautonlaundry.dtos.responses.referralresponse;

import com.work.mautonlaundry.data.model.ReferralPaymentRuleHistory;

import java.time.LocalDateTime;

public record RuleHistoryResponse(
        String id,
        String referrerId,
        String changedBy,
        String previousRules,
        String newRules,
        LocalDateTime changedAt,
        String changeReason
) {
    public static RuleHistoryResponse from(ReferralPaymentRuleHistory h) {
        return new RuleHistoryResponse(
                h.getId(), h.getReferrerId(), h.getChangedBy(),
                h.getPreviousRules(), h.getNewRules(),
                h.getChangedAt(), h.getChangeReason());
    }
}

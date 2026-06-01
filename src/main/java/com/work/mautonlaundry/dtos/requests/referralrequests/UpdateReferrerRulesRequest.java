package com.work.mautonlaundry.dtos.requests.referralrequests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Bulk-replace a referrer's payment rules. Records a history entry, deactivates
 * the previous rule set and activates the new one.
 */
public record UpdateReferrerRulesRequest(
        @NotNull @Valid List<ReferralRuleRequest> rules,
        String changeReason
) {
}

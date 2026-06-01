package com.work.mautonlaundry.dtos.requests.referralrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A manual adjustment to a PENDING payout. Amount may be negative. */
public record PayoutAdjustmentRequest(
        @NotNull BigDecimal amount,
        @NotBlank String reason
) {
}

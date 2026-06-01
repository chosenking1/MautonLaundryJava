package com.work.mautonlaundry.dtos.requests.referralrequests;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GeneratePayoutRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}

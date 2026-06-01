package com.work.mautonlaundry.dtos.requests.referralrequests;

import jakarta.validation.constraints.NotBlank;

public record MarkPaidRequest(
        @NotBlank String paymentReference
) {
}

package com.work.mautonlaundry.dtos.responses.handoffresponses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandoffRedemptionResponse {
    private String bookingId;
    private String stage;
    private String fromStatus;
    private String toStatus;
    private Instant redeemedAt;
    private NextStageHandoff nextStage;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NextStageHandoff {
        private String stage;
        private Instant expiresAt;
        // Note: code itself is intentionally not echoed — the rider sees it via
        // the customer/laundry on next poll, not via this response.
    }
}

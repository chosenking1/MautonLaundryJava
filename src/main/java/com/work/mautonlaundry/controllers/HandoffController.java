package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.dtos.requests.handoffrequests.RedeemHandoffCodeRequest;
import com.work.mautonlaundry.dtos.responses.handoffresponses.HandoffRedemptionResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.HandoffCodeService;
import com.work.mautonlaundry.services.HandoffCodeService.RedemptionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/handoff")
@RequiredArgsConstructor
public class HandoffController {

    private final HandoffCodeService handoffCodeService;

    @PostMapping("/redeem")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    public ResponseEntity<HandoffRedemptionResponse> redeem(
            @Valid @RequestBody RedeemHandoffCodeRequest request) {
        AppUser rider = SecurityUtil.getCurrentUser().orElseThrow();
        RedemptionResult result = handoffCodeService.redeem(
                request.getBookingId(), request.getCode(), rider);

        HandoffRedemptionResponse.HandoffRedemptionResponseBuilder builder = HandoffRedemptionResponse.builder()
                .bookingId(result.booking().getId())
                .stage(result.redeemedCode().getStage().name())
                .fromStatus(result.fromStatus().name())
                .toStatus(result.toStatus().name())
                .redeemedAt(result.redeemedCode().getRedeemedAt());

        if (result.nextStageCode() != null) {
            builder.nextStage(HandoffRedemptionResponse.NextStageHandoff.builder()
                    .stage(result.nextStageCode().getStage().name())
                    .expiresAt(result.nextStageCode().getExpiresAt())
                    .build());
        }

        return ResponseEntity.ok(builder.build());
    }
}

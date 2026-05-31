package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.dtos.responses.referralresponse.MyReferralDashboardResponse;
import com.work.mautonlaundry.dtos.responses.referralresponse.ShareDetailsResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing referral endpoints. Only usable by a logged-in user whose
 * email matches an active referrer record; otherwise the service returns 403.
 */
@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/my-dashboard")
    public ResponseEntity<MyReferralDashboardResponse> myDashboard() {
        String email = currentEmailOrThrow();
        return ResponseEntity.ok(referralService.getMyDashboard(email));
    }

    @GetMapping("/my-share-details")
    public ResponseEntity<ShareDetailsResponse> myShareDetails() {
        String email = currentEmailOrThrow();
        return ResponseEntity.ok(referralService.getMyShareDetails(email));
    }

    private String currentEmailOrThrow() {
        return SecurityUtil.getCurrentUser()
                .map(AppUser::getEmail)
                .orElseThrow(() -> new com.work.mautonlaundry.exceptions.UnauthorizedException("Not authenticated"));
    }
}

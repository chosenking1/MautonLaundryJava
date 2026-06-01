package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.dtos.requests.referralrequests.*;
import com.work.mautonlaundry.dtos.responses.referralresponse.*;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/referrals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReferralController {

    private final ReferralService referralService;

    // ---- Referrers ----

    @GetMapping
    public ResponseEntity<Page<ReferrerListItemResponse>> list(
            @RequestParam(required = false) ReferrerType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(referralService.listReferrers(type, active, search, pageable));
    }

    @PostMapping
    public ResponseEntity<ReferrerResponse> create(@Valid @RequestBody CreateReferrerRequest request) {
        ReferrerResponse response = referralService.createReferrer(request, SecurityUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReferrerResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(referralService.getReferrer(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReferrerResponse> update(@PathVariable String id,
                                                   @Valid @RequestBody UpdateReferrerRequest request) {
        return ResponseEntity.ok(referralService.updateReferrer(id, request));
    }

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<AdminReferrerDashboardResponse> dashboard(@PathVariable String id) {
        return ResponseEntity.ok(referralService.getReferrerDashboard(id));
    }

    @GetMapping("/{id}/customers")
    public ResponseEntity<List<ReferralCustomerResponse>> customers(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean onlyRepeat) {
        return ResponseEntity.ok(referralService.getReferrerCustomers(id, onlyRepeat));
    }

    // ---- Rules ----

    @GetMapping("/{id}/rules")
    public ResponseEntity<List<ReferralRuleResponse>> rules(@PathVariable String id) {
        return ResponseEntity.ok(referralService.getRules(id));
    }

    @PostMapping("/{id}/rules")
    public ResponseEntity<ReferralRuleResponse> addRule(@PathVariable String id,
                                                        @Valid @RequestBody ReferralRuleRequest request) {
        ReferralRuleResponse response = referralService.addRule(id, request, SecurityUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/rules/{ruleId}")
    public ResponseEntity<ReferralRuleResponse> updateRule(@PathVariable String id,
                                                           @PathVariable String ruleId,
                                                           @Valid @RequestBody ReferralRuleRequest request) {
        return ResponseEntity.ok(referralService.updateRule(id, ruleId, request, SecurityUtil.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    public ResponseEntity<Void> deactivateRule(@PathVariable String id, @PathVariable String ruleId) {
        referralService.deactivateRule(id, ruleId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/rules")
    public ResponseEntity<List<ReferralRuleResponse>> replaceRules(
            @PathVariable String id,
            @Valid @RequestBody UpdateReferrerRulesRequest request) {
        return ResponseEntity.ok(referralService.updateReferrerRules(id, request, SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/{id}/rules/history")
    public ResponseEntity<List<RuleHistoryResponse>> ruleHistory(@PathVariable String id) {
        return ResponseEntity.ok(referralService.getRuleHistory(id));
    }

    // ---- Payouts ----

    @GetMapping("/{id}/payouts")
    public ResponseEntity<List<PayoutResponse>> payouts(@PathVariable String id) {
        return ResponseEntity.ok(referralService.getPayouts(id));
    }

    @PostMapping("/{id}/payouts/generate")
    public ResponseEntity<PayoutResponse> generatePayout(@PathVariable String id,
                                                         @Valid @RequestBody GeneratePayoutRequest request) {
        PayoutResponse response = referralService.generatePayoutRecord(
                id, request.fromDate(), request.toDate(), SecurityUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/payouts/preview")
    public ResponseEntity<PayoutResponse> previewPayout(
            @PathVariable String id,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(referralService.previewPayoutCalculation(id, fromDate, toDate));
    }

    @PostMapping("/{id}/payouts/{payoutId}/approve")
    public ResponseEntity<PayoutResponse> approve(@PathVariable String id, @PathVariable String payoutId) {
        return ResponseEntity.ok(referralService.approvePayoutRecord(payoutId, SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/{id}/payouts/{payoutId}/mark-paid")
    public ResponseEntity<PayoutResponse> markPaid(@PathVariable String id, @PathVariable String payoutId,
                                                   @Valid @RequestBody MarkPaidRequest request) {
        return ResponseEntity.ok(
                referralService.markPayoutAsPaid(payoutId, SecurityUtil.getCurrentUserId(), request.paymentReference()));
    }

    @PostMapping("/{id}/payouts/{payoutId}/adjustment")
    public ResponseEntity<PayoutResponse> adjustment(@PathVariable String id, @PathVariable String payoutId,
                                                     @Valid @RequestBody PayoutAdjustmentRequest request) {
        return ResponseEntity.ok(referralService.addManualAdjustment(
                payoutId, request.amount(), request.reason(), SecurityUtil.getCurrentUserId()));
    }

    // ---- Cross-referrer dashboards ----

    @GetMapping("/pending-payouts")
    public ResponseEntity<List<PendingPayoutResponse>> pendingPayouts() {
        return ResponseEntity.ok(referralService.getPendingPayouts());
    }

    @GetMapping("/milestone-tracker")
    public ResponseEntity<List<MilestoneTrackerItemResponse>> milestoneTracker() {
        return ResponseEntity.ok(referralService.getMilestoneTracker());
    }
}

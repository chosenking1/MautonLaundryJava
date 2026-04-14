package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Discount;
import com.work.mautonlaundry.data.model.DiscountUserAssignment;
import com.work.mautonlaundry.data.model.enums.ApprovalStatus;
import com.work.mautonlaundry.data.repository.DiscountRepository;
import com.work.mautonlaundry.data.repository.DiscountUserAssignmentRepository;
import com.work.mautonlaundry.dtos.responses.discount.DiscountApplicationResult;
import com.work.mautonlaundry.dtos.responses.discount.DiscountEligibilityResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountRepository discountRepository;
    private final DiscountUserAssignmentRepository assignmentRepository;

    @GetMapping("/check")
    public ResponseEntity<DiscountEligibilityResponse> checkCode(
            @RequestParam String code,
            @RequestParam(required = false) BigDecimal orderValue) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        DiscountEligibilityResponse response = discountService.checkCodeEligibility(code, userId, orderValue);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/apply")
    public ResponseEntity<DiscountApplicationResult> applyDiscount(
            @RequestBody Map<String, Object> request) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        String code = (String) request.get("code");
        String bookingId = (String) request.get("bookingId");
        BigDecimal orderValue = new BigDecimal(request.get("orderValue").toString());
        
        DiscountApplicationResult result = discountService.applyDiscountAtCheckout(
                code, userId, bookingId, orderValue);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DiscountUserAssignment>> getPendingApprovals(
            @RequestParam(required = false) String discountId,
            Pageable pageable) {
        Page<DiscountUserAssignment> page;
        if (discountId != null) {
            page = assignmentRepository.findByDiscountIdAndApprovalStatus(
                    discountId, ApprovalStatus.PENDING, pageable);
        } else {
            page = assignmentRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable);
        }
        return ResponseEntity.ok(page);
    }

    @PostMapping("/approvals/{assignmentId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveAssignment(@PathVariable String assignmentId) {
        String adminId = SecurityUtil.getCurrentUserId();
        discountService.approveAssignment(assignmentId, adminId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/approvals/{assignmentId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectAssignment(
            @PathVariable String assignmentId,
            @RequestBody Map<String, String> request) {
        String adminId = SecurityUtil.getCurrentUserId();
        String reason = request.get("reason");
        discountService.rejectAssignment(assignmentId, adminId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/assignments/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeAssignment(@RequestBody Map<String, String> request) {
        String adminId = SecurityUtil.getCurrentUserId();
        String discountId = request.get("discountId");
        String userId = request.get("userId");
        discountService.revokeAssignment(discountId, userId, adminId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{discountId}/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DiscountUserAssignment>> getAssignments(
            @PathVariable String discountId,
            @RequestParam(required = false) ApprovalStatus status,
            Pageable pageable) {
        Page<DiscountUserAssignment> page;
        if (status != null) {
            page = assignmentRepository.findByDiscountIdAndApprovalStatus(discountId, status, pageable);
        } else {
            page = assignmentRepository.findByDiscountId(discountId, pageable);
        }
        return ResponseEntity.ok(page);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Discount> createDiscount(@RequestBody Discount request) {
        String adminId = SecurityUtil.getCurrentUserId();
        request.setCreatedBy(adminId);
        request.setActive(true);
        Discount saved = discountRepository.save(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Discount>> getAllDiscounts(Pageable pageable) {
        return ResponseEntity.ok(discountRepository.findAll(pageable));
    }

    @GetMapping("/{discountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Discount> getDiscount(@PathVariable String discountId) {
        return discountRepository.findById(discountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{discountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Discount> updateDiscount(
            @PathVariable String discountId,
            @RequestBody Discount request) {
        return discountRepository.findById(discountId)
                .map(existing -> {
                    existing.setName(request.getName());
                    existing.setDescription(request.getDescription());
                    existing.setDiscountType(request.getDiscountType());
                    existing.setDiscountValue(request.getDiscountValue());
                    existing.setMinimumOrderValue(request.getMinimumOrderValue());
                    existing.setMaximumOrderValue(request.getMaximumOrderValue());
                    existing.setRequiresApproval(request.isRequiresApproval());
                    existing.setAllowedEmailDomain(request.getAllowedEmailDomain());
                    existing.setMaxUsesPerUser(request.getMaxUsesPerUser());
                    existing.setMaxTotalUses(request.getMaxTotalUses());
                    existing.setResetPeriod(request.getResetPeriod());
                    existing.setValidFrom(request.getValidFrom());
                    existing.setValidUntil(request.getValidUntil());
                    existing.setActive(request.isActive());
                    return ResponseEntity.ok(discountRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
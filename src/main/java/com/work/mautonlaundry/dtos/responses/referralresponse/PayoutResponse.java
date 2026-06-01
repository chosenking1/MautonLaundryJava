package com.work.mautonlaundry.dtos.responses.referralresponse;

import com.work.mautonlaundry.data.model.ReferralPayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayoutResponse(
        String id,
        String referrerId,
        LocalDate periodFrom,
        LocalDate periodTo,
        String calculationBreakdown,
        BigDecimal totalCommissionCalculated,
        BigDecimal manualAdjustmentAmount,
        String manualAdjustmentReason,
        BigDecimal finalPayoutAmount,
        String status,
        LocalDateTime generatedAt,
        String generatedBy,
        String approvedBy,
        LocalDateTime approvedAt,
        LocalDateTime paidAt,
        String paidBy,
        String paymentReference,
        String notes
) {
    public static PayoutResponse from(ReferralPayout p) {
        return new PayoutResponse(
                p.getId(), p.getReferrerId(), p.getPeriodFrom(), p.getPeriodTo(),
                p.getCalculationBreakdown(), p.getTotalCommissionCalculated(),
                p.getManualAdjustmentAmount(), p.getManualAdjustmentReason(),
                p.getFinalPayoutAmount(), p.getStatus().name(),
                p.getGeneratedAt(), p.getGeneratedBy(),
                p.getApprovedBy(), p.getApprovedAt(),
                p.getPaidAt(), p.getPaidBy(), p.getPaymentReference(), p.getNotes());
    }
}

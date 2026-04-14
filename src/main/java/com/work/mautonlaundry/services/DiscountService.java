package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Discount;
import com.work.mautonlaundry.data.model.DiscountUserAssignment;
import com.work.mautonlaundry.data.model.DiscountUsageLog;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.ApprovalStatus;
import com.work.mautonlaundry.data.model.enums.DiscountCheckResult;
import com.work.mautonlaundry.data.model.enums.DiscountType;
import com.work.mautonlaundry.data.model.enums.ResetPeriod;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DiscountRepository;
import com.work.mautonlaundry.data.repository.DiscountUserAssignmentRepository;
import com.work.mautonlaundry.data.repository.DiscountUsageLogRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.discount.DiscountApplicationResult;
import com.work.mautonlaundry.dtos.responses.discount.DiscountEligibilityResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final DiscountUserAssignmentRepository assignmentRepository;
    private final DiscountUsageLogRepository usageLogRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public DiscountEligibilityResponse checkCodeEligibility(String code, String userId, BigDecimal orderValue) {
        Optional<Discount> discountOpt = discountRepository.findByCodeIgnoreCase(code.trim());
        if (discountOpt.isEmpty()) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.INVALID_CODE, "Code not found");
        }

        Discount discount = discountOpt.get();
        if (!discount.isActive()) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.INACTIVE, "This code is no longer active");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(discount.getValidFrom())) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.NOT_YET_VALID, "This code is not yet active");
        }
        if (discount.getValidUntil() != null && now.isAfter(discount.getValidUntil())) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.EXPIRED, "This code has expired");
        }

        if (discount.getMaxTotalUses() != null && discount.getCurrentTotalUses() >= discount.getMaxTotalUses()) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.TOTAL_USAGE_LIMIT_REACHED, "This code has reached its usage limit");
        }

        Optional<AppUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.INVALID_CODE, "User not found");
        }
        AppUser user = userOpt.get();

        if (discount.getAllowedEmailDomain() != null) {
            String userDomain = extractDomain(user.getEmail());
            if (!userDomain.equalsIgnoreCase(discount.getAllowedEmailDomain())) {
                return DiscountEligibilityResponse.ineligible(DiscountCheckResult.DOMAIN_MISMATCH, 
                        "This code is only available to verified company staff");
            }
        }

        Optional<DiscountUserAssignment> assignmentOpt = assignmentRepository.findByDiscountIdAndUserId(discount.getId(), userId);
        if (assignmentOpt.isPresent()) {
            DiscountUserAssignment assignment = assignmentOpt.get();
            maybeResetUsageCounter(assignment, discount);

            switch (assignment.getApprovalStatus()) {
                case PENDING:
                    return DiscountEligibilityResponse.ineligible(DiscountCheckResult.PENDING_APPROVAL, 
                            "Your discount is being verified. You will be notified when it is ready.");
                case REJECTED:
                    return DiscountEligibilityResponse.ineligible(DiscountCheckResult.REJECTED, 
                            "Your discount code could not be verified. Please contact your HR team.");
                case REVOKED:
                    return DiscountEligibilityResponse.ineligible(DiscountCheckResult.REVOKED, 
                            "Your access to this discount has been removed.");
                case APPROVED:
                    if (assignment.getUsageCountCurrent() >= discount.getMaxUsesPerUser()) {
                        return DiscountEligibilityResponse.ineligible(DiscountCheckResult.USAGE_LIMIT_REACHED, 
                                "You have reached the usage limit for this code.");
                    }
                    break;
            }
        } else if (discount.isRequiresApproval()) {
            DiscountUserAssignment assignment = DiscountUserAssignment.builder()
                    .discountId(discount.getId())
                    .userId(userId)
                    .approvalStatus(ApprovalStatus.PENDING)
                    .usageCountCurrent(0)
                    .usageCountLifetime(0)
                    .build();
            assignmentRepository.save(assignment);
            return DiscountEligibilityResponse.ineligible(DiscountCheckResult.PENDING_APPROVAL, 
                    "Your discount is being verified. You will be notified when it is ready.");
        }

        if (orderValue != null) {
            if (discount.getMinimumOrderValue() != null && orderValue.compareTo(discount.getMinimumOrderValue()) < 0) {
                return DiscountEligibilityResponse.ineligible(DiscountCheckResult.ORDER_TOO_LOW, 
                        "Minimum order value of ₦" + discount.getMinimumOrderValue() + " required for this code");
            }
            if (discount.getMaximumOrderValue() != null && orderValue.compareTo(discount.getMaximumOrderValue()) > 0) {
                return DiscountEligibilityResponse.ineligible(DiscountCheckResult.ORDER_TOO_HIGH, 
                        "This code applies to orders up to ₦" + discount.getMaximumOrderValue());
            }
        }

        BigDecimal discountAmount = orderValue != null ? calculateDiscountAmount(discount, orderValue) : null;
        Integer remaining = assignmentOpt.map(a -> discount.getMaxUsesPerUser() - a.getUsageCountCurrent()).orElse(discount.getMaxUsesPerUser());

        return DiscountEligibilityResponse.eligible(
                discount.getCode(),
                discount.getName(),
                discount.getDiscountType(),
                discount.getDiscountValue(),
                discountAmount,
                remaining,
                discount.getMaxUsesPerUser()
        );
    }

    @Transactional
    public DiscountApplicationResult applyDiscountAtCheckout(String code, String userId, String bookingId, BigDecimal orderValue) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("You can only apply discounts to your own bookings");
        }

        BigDecimal canonicalOrderValue = booking.getTotalPrice();
        if (canonicalOrderValue == null || canonicalOrderValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Booking total is not available for discounting");
        }
        if (orderValue != null && canonicalOrderValue.compareTo(orderValue) != 0) {
            log.warn("Discount apply order value mismatch: bookingId={}, provided={}, canonical={}",
                    bookingId, orderValue, canonicalOrderValue);
        }
        if (booking.getDiscountCode() != null && !booking.getDiscountCode().isBlank()
                && !booking.getDiscountCode().equalsIgnoreCase(code.trim())) {
            return DiscountApplicationResult.rejected(
                    DiscountCheckResult.INVALID_CODE,
                    "A different discount has already been applied to this booking"
            );
        }

        DiscountEligibilityResponse eligibility = checkCodeEligibility(code, userId, canonicalOrderValue);
        
        if (!eligibility.isEligible()) {
            log.warn("Discount checkout rejection: code={}, userId={}, result={}", code, userId, eligibility.getResult());
            return DiscountApplicationResult.rejected(eligibility.getResult(), eligibility.getMessage());
        }

        Discount discount = discountRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new IllegalStateException("Discount not found"));
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        DiscountUserAssignment assignment = assignmentRepository.findByDiscountIdAndUserId(discount.getId(), userId)
                .orElseGet(() -> createApprovedAssignment(discount, user));

        BigDecimal discountAmount = calculateDiscountAmount(discount, canonicalOrderValue);
        BigDecimal finalOrderValue = canonicalOrderValue.subtract(discountAmount);

        assignment.setUsageCountCurrent(assignment.getUsageCountCurrent() + 1);
        assignment.setUsageCountLifetime(assignment.getUsageCountLifetime() + 1);
        assignment.setLastUsedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);

        discount.setCurrentTotalUses(discount.getCurrentTotalUses() + 1);
        discountRepository.save(discount);

        booking.setDiscountCode(discount.getCode());
        booking.setDiscountAmount(discountAmount);
        booking.setFinalAmount(finalOrderValue);
        bookingRepository.save(booking);

        DiscountUsageLog usageLog = DiscountUsageLog.builder()
                .discountId(discount.getId())
                .userId(userId)
                .bookingId(bookingId)
                .assignmentId(assignment.getId())
                .discountCodeSnapshot(discount.getCode())
                .discountTypeSnapshot(discount.getDiscountType().name())
                .discountValueSnapshot(discount.getDiscountValue())
                .orderValueBefore(canonicalOrderValue)
                .discountAmountApplied(discountAmount)
                .orderValueAfter(finalOrderValue)
                .build();
        usageLogRepository.save(usageLog);

        return DiscountApplicationResult.applied(
                discountAmount,
                finalOrderValue,
                assignment.getUsageCountCurrent(),
                discount.getMaxUsesPerUser()
        );
    }

    @Transactional
    public void approveAssignment(String assignmentId, String approvedByAdminId) {
        DiscountUserAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (assignment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only PENDING assignments can be approved");
        }

        assignment.setApprovalStatus(ApprovalStatus.APPROVED);
        assignment.setApprovedBy(approvedByAdminId);
        assignment.setApprovedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);

        AppUser user = userRepository.findById(assignment.getUserId()).orElse(null);
        if (user != null) {
            emailService.sendDiscountApprovedEmail(user.getEmail(), "IMOTOTO Discount");
        }
    }

    @Transactional
    public void rejectAssignment(String assignmentId, String rejectedByAdminId, String internalReason) {
        DiscountUserAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        assignment.setApprovalStatus(ApprovalStatus.REJECTED);
        assignment.setApprovedBy(rejectedByAdminId);
        assignment.setApprovedAt(LocalDateTime.now());
        assignment.setRejectedReason(internalReason);
        assignmentRepository.save(assignment);
    }

    @Transactional
    public void revokeAssignment(String discountId, String userId, String revokedByAdminId) {
        DiscountUserAssignment assignment = assignmentRepository.findByDiscountIdAndUserId(discountId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        assignment.setApprovalStatus(ApprovalStatus.REVOKED);
        assignment.setApprovedBy(revokedByAdminId);
        assignment.setApprovedAt(LocalDateTime.now());
        assignment.setRejectedReason("Staff member access revoked");
        assignmentRepository.save(assignment);
    }

    private BigDecimal calculateDiscountAmount(Discount discount, BigDecimal orderValue) {
        BigDecimal raw;
        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            raw = orderValue.multiply(discount.getDiscountValue()).divide(BigDecimal.valueOf(100));
        } else {
            raw = discount.getDiscountValue();
        }
        return raw.min(orderValue);
    }

    private String extractDomain(String email) {
        int atIndex = email.indexOf('@');
        return atIndex >= 0 ? email.substring(atIndex + 1).toLowerCase() : "";
    }

    private DiscountUserAssignment createApprovedAssignment(Discount discount, AppUser user) {
        DiscountUserAssignment assignment = DiscountUserAssignment.builder()
                .discountId(discount.getId())
                .userId(user.getId())
                .approvalStatus(ApprovalStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .usageCountCurrent(0)
                .usageCountLifetime(0)
                .lastResetAt(LocalDateTime.now())
                .build();
        return assignmentRepository.save(assignment);
    }

    private void maybeResetUsageCounter(DiscountUserAssignment assignment, Discount discount) {
        if (discount.getResetPeriod() == ResetPeriod.NEVER) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReset = assignment.getLastResetAt();
        if (lastReset == null) {
            assignment.setLastResetAt(now);
            return;
        }

        boolean shouldReset = switch (discount.getResetPeriod()) {
            case DAILY -> lastReset.toLocalDate().isBefore(now.toLocalDate());
            case WEEKLY -> lastReset.isBefore(getStartOfCurrentWeek());
            case MONTHLY -> lastReset.isBefore(getStartOfCurrentMonth());
            case YEARLY -> lastReset.isBefore(getStartOfCurrentYear());
            default -> false;
        };

        if (shouldReset) {
            assignment.setUsageCountCurrent(0);
            assignment.setLastResetAt(now);
            assignmentRepository.save(assignment);
        }
    }

    private LocalDateTime getStartOfCurrentWeek() {
        LocalDateTime now = LocalDateTime.now();
        return now.minusDays(now.getDayOfWeek().getValue() - 1).withHour(0).withMinute(0).withSecond(0);
    }

    private LocalDateTime getStartOfCurrentMonth() {
        return LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
    }

    private LocalDateTime getStartOfCurrentYear() {
        return LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
    }
}

package com.work.mautonlaundry.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.model.enums.ReferralPayoutStatus;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.referralrequests.*;
import com.work.mautonlaundry.dtos.responses.referralresponse.*;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Standalone referral system: partner referrers, attribution, per-booking
 * commission calculation, and the full payout lifecycle. Separate from the
 * discount system but optionally linked to it for welcome incentives.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private final ReferrerRepository referrerRepository;
    private final ReferralAttributionRepository attributionRepository;
    private final ReferralPaymentRuleRepository ruleRepository;
    private final ReferralPaymentRuleHistoryRepository ruleHistoryRepository;
    private final ReferralBookingEventRepository bookingEventRepository;
    private final ReferralPayoutRepository payoutRepository;
    private final ReferralCalculationEngine calculationEngine;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PricingEngine pricingEngine;
    private final DiscountService discountService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${app.referral.milestone-target:100}")
    private int milestoneTarget;

    @Value("${app.referral.play-store-link:https://play.google.com/store/apps/details?id=ng.com.imototo}")
    private String playStoreLink;

    // ------------------------------------------------------------------
    // Referrer management
    // ------------------------------------------------------------------

    @Transactional
    public ReferrerResponse createReferrer(CreateReferrerRequest request, String adminId) {
        String code = (request.referralCode() == null || request.referralCode().isBlank())
                ? generateReferralCode(request.name())
                : request.referralCode().trim().toUpperCase();

        if (referrerRepository.existsByReferralCodeIgnoreCase(code)) {
            throw new ConflictException("Referral code already in use: " + code);
        }
        if (request.linkedDiscountId() != null && !request.linkedDiscountId().isBlank()) {
            discountService.getDiscountById(request.linkedDiscountId())
                    .orElseThrow(() -> new IllegalArgumentException("Linked discount not found"));
        }

        Referrer referrer = Referrer.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .referrerType(request.referrerType())
                .referralCode(code)
                .linkedDiscountId(emptyToNull(request.linkedDiscountId()))
                .active(true)
                .notes(request.notes())
                .build();
        referrer = referrerRepository.save(referrer);

        if (request.rules() != null) {
            for (ReferralRuleRequest ruleReq : request.rules()) {
                ruleRepository.save(buildRule(referrer.getId(), ruleReq, adminId));
            }
        }
        return getReferrer(referrer.getId());
    }

    @Transactional
    public ReferrerResponse updateReferrer(String referrerId, UpdateReferrerRequest request) {
        Referrer referrer = requireReferrer(referrerId);
        if (request.name() != null) referrer.setName(request.name());
        if (request.email() != null) referrer.setEmail(request.email());
        if (request.phone() != null) referrer.setPhone(request.phone());
        if (request.referrerType() != null) referrer.setReferrerType(request.referrerType());
        if (request.active() != null) referrer.setActive(request.active());
        if (request.linkedDiscountId() != null) {
            if (request.linkedDiscountId().isBlank()) {
                referrer.setLinkedDiscountId(null);
            } else {
                discountService.getDiscountById(request.linkedDiscountId())
                        .orElseThrow(() -> new IllegalArgumentException("Linked discount not found"));
                referrer.setLinkedDiscountId(request.linkedDiscountId());
            }
        }
        if (request.notes() != null) referrer.setNotes(request.notes());
        referrerRepository.save(referrer);
        return getReferrer(referrerId);
    }

    @Transactional(readOnly = true)
    public ReferrerResponse getReferrer(String referrerId) {
        Referrer referrer = requireReferrer(referrerId);
        List<ReferralRuleResponse> rules = ruleRepository.findByReferrerId(referrerId).stream()
                .map(ReferralRuleResponse::from).toList();
        return ReferrerResponse.from(referrer, rules);
    }

    @Transactional(readOnly = true)
    public Page<ReferrerListItemResponse> listReferrers(ReferrerType type, Boolean active,
                                                        String search, Pageable pageable) {
        Page<Referrer> page;
        if (search != null && !search.isBlank()) {
            page = referrerRepository.search(search.trim(), pageable);
        } else if (type != null && active != null) {
            page = referrerRepository.findByReferrerTypeAndActive(type, active, pageable);
        } else if (type != null) {
            page = referrerRepository.findByReferrerType(type, pageable);
        } else if (active != null) {
            page = referrerRepository.findByActive(active, pageable);
        } else {
            page = referrerRepository.findAll(pageable);
        }
        return page.map(this::toListItem);
    }

    private ReferrerListItemResponse toListItem(Referrer r) {
        long customers = attributionRepository.countByReferrerId(r.getId());
        long repeat = bookingEventRepository.findRepeatAttributionIds(r.getId()).size();
        BigDecimal pending = payoutRepository.sumFinalAmountByReferrerAndStatusIn(
                r.getId(), List.of(ReferralPayoutStatus.PENDING, ReferralPayoutStatus.APPROVED));
        LocalDateTime lastPaid = payoutRepository.findByReferrerIdOrderByGeneratedAtDesc(r.getId()).stream()
                .filter(p -> p.getStatus() == ReferralPayoutStatus.PAID && p.getPaidAt() != null)
                .map(ReferralPayout::getPaidAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ReferrerListItemResponse(r.getId(), r.getName(), r.getReferrerType().name(),
                r.getReferralCode(), customers, repeat, pending, lastPaid, r.isActive());
    }

    /** Anonymised list of a referrer's referred customers (Customer #1, #2, ...). */
    @Transactional(readOnly = true)
    public List<ReferralCustomerResponse> getReferrerCustomers(String referrerId, boolean onlyRepeat) {
        requireReferrer(referrerId);
        List<ReferralAttribution> attributions = attributionRepository.findByReferrerId(referrerId);
        attributions.sort(Comparator.comparing(ReferralAttribution::getRegisteredAt));

        List<ReferralCustomerResponse> result = new ArrayList<>();
        int index = 0;
        for (ReferralAttribution attr : attributions) {
            index++;
            List<ReferralBookingEvent> events =
                    bookingEventRepository.findByAttributionIdOrderByCreatedAtDesc(attr.getId());
            if (onlyRepeat && events.size() < 2) continue;

            BigDecimal orderValue = events.stream()
                    .map(e -> e.getOrderGrossValue() == null ? BigDecimal.ZERO : e.getOrderGrossValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal commission = events.stream()
                    .map(e -> e.getReferrerCommissionEarned() == null ? BigDecimal.ZERO : e.getReferrerCommissionEarned())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDateTime lastBooking = events.isEmpty() ? null : events.get(0).getCreatedAt();

            result.add(new ReferralCustomerResponse(
                    "Customer #" + index,
                    attr.getRegisteredAt(),
                    events.size(),
                    lastBooking,
                    orderValue,
                    commission));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Payment rules
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReferralRuleResponse> getRules(String referrerId) {
        requireReferrer(referrerId);
        return ruleRepository.findByReferrerId(referrerId).stream().map(ReferralRuleResponse::from).toList();
    }

    @Transactional
    public ReferralRuleResponse addRule(String referrerId, ReferralRuleRequest request, String adminId) {
        Referrer referrer = requireReferrer(referrerId);
        String before = serialiseRules(referrerId);
        ReferralPaymentRule rule = ruleRepository.save(buildRule(referrer.getId(), request, adminId));
        recordRuleHistory(referrerId, before, serialiseRules(referrerId), adminId, "Rule added");
        return ReferralRuleResponse.from(rule);
    }

    @Transactional
    public ReferralRuleResponse updateRule(String referrerId, String ruleId,
                                           ReferralRuleRequest request, String adminId) {
        requireReferrer(referrerId);
        ReferralPaymentRule existing = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new NoSuchElementException("Rule not found"));
        if (!existing.getReferrerId().equals(referrerId)) {
            throw new IllegalArgumentException("Rule does not belong to this referrer");
        }
        String before = serialiseRules(referrerId);
        applyRuleFields(existing, request);
        ReferralPaymentRule saved = ruleRepository.save(existing);
        recordRuleHistory(referrerId, before, serialiseRules(referrerId), adminId, "Rule updated");
        return ReferralRuleResponse.from(saved);
    }

    @Transactional
    public void deactivateRule(String referrerId, String ruleId, String adminId) {
        requireReferrer(referrerId);
        ReferralPaymentRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new NoSuchElementException("Rule not found"));
        if (!rule.getReferrerId().equals(referrerId)) {
            throw new IllegalArgumentException("Rule does not belong to this referrer");
        }
        String before = serialiseRules(referrerId);
        rule.setActive(false);
        ruleRepository.save(rule);
        recordRuleHistory(referrerId, before, serialiseRules(referrerId), adminId, "Rule deactivated");
    }

    /** Bulk-replace a referrer's rules: deactivate the current set, activate the new one, record history. */
    @Transactional
    public List<ReferralRuleResponse> updateReferrerRules(String referrerId,
                                                          UpdateReferrerRulesRequest request, String adminId) {
        requireReferrer(referrerId);
        String before = serialiseRules(referrerId);

        for (ReferralPaymentRule old : ruleRepository.findByReferrerIdAndActiveTrue(referrerId)) {
            old.setActive(false);
            ruleRepository.save(old);
        }
        List<ReferralRuleResponse> created = new ArrayList<>();
        for (ReferralRuleRequest ruleReq : request.rules()) {
            created.add(ReferralRuleResponse.from(ruleRepository.save(buildRule(referrerId, ruleReq, adminId))));
        }
        recordRuleHistory(referrerId, before, serialiseRules(referrerId), adminId,
                request.changeReason() == null ? "Rules replaced" : request.changeReason());
        return created;
    }

    @Transactional(readOnly = true)
    public List<RuleHistoryResponse> getRuleHistory(String referrerId) {
        requireReferrer(referrerId);
        return ruleHistoryRepository.findByReferrerIdOrderByChangedAtDesc(referrerId).stream()
                .map(RuleHistoryResponse::from).toList();
    }

    // ------------------------------------------------------------------
    // Attribution at registration
    // ------------------------------------------------------------------

    /**
     * Validates a referral code and attributes the user to the referrer. Sets up
     * the linked welcome discount if one is configured. Throws if the code is
     * invalid or the user already has an attribution (one referrer per user). The
     * registration caller invokes this non-blocking — a failure here must never
     * fail signup.
     */
    @Transactional
    public void applyReferralAtRegistration(String userId, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return;
        }
        Referrer referrer = referrerRepository.findByReferralCodeIgnoreCase(referralCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        if (!referrer.isActive()) {
            throw new IllegalArgumentException("Referral code is not active");
        }
        if (attributionRepository.existsByUserId(userId)) {
            throw new ConflictException("User already has a referral attribution");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        ReferralAttribution attribution = ReferralAttribution.builder()
                .referrerId(referrer.getId())
                .userId(userId)
                .registeredAt(LocalDateTime.now())
                .referralCodeUsed(referrer.getReferralCode())
                .build();
        attributionRepository.save(attribution);

        if (referrer.getLinkedDiscountId() != null) {
            discountService.assignWelcomeDiscount(referrer.getLinkedDiscountId(), user);
        }
        log.info("Attributed user {} to referrer {} via code {}", userId, referrer.getId(), referrer.getReferralCode());
    }

    // ------------------------------------------------------------------
    // Booking event recording (called from the payment webhook, non-blocking)
    // ------------------------------------------------------------------

    @Transactional
    public void recordReferralBookingEvent(String bookingId) {
        if (bookingEventRepository.existsByBookingId(bookingId)) {
            return; // idempotent: webhook may be delivered more than once
        }
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getUser() == null) {
            return;
        }
        ReferralAttribution attribution =
                attributionRepository.findByUserId(booking.getUser().getId()).orElse(null);
        if (attribution == null) {
            return; // customer was not referred
        }

        int bookingNumber = (int) bookingEventRepository.countByAttributionId(attribution.getId()) + 1;
        int daysSinceRegistration =
                (int) ChronoUnit.DAYS.between(attribution.getRegisteredAt().toLocalDate(), LocalDate.now());
        if (daysSinceRegistration < 0) daysSinceRegistration = 0;

        BigDecimal imototoCommission = pricingEngine.calculateImototoCommissionForBooking(
                booking.getTotalPrice(), booking.getDeliveryFee(), booking.getDiscountAmount());
        BigDecimal orderGrossValue =
                booking.getFinalAmount() != null ? booking.getFinalAmount() : booking.getTotalPrice();

        ReferralCalculationEngine.CalculationContext ctx = new ReferralCalculationEngine.CalculationContext(
                booking.getCreatedAt() != null ? booking.getCreatedAt().toLocalDate() : LocalDate.now(),
                bookingNumber, daysSinceRegistration, imototoCommission, orderGrossValue);
        ReferralCalculationEngine.CalculationResult result =
                calculationEngine.calculate(attribution.getReferrerId(), ctx);

        ReferralBookingEvent event = ReferralBookingEvent.builder()
                .attributionId(attribution.getId())
                .bookingId(bookingId)
                .bookingNumber(bookingNumber)
                .daysSinceRegistration(daysSinceRegistration)
                .imototoCommissionOnOrder(imototoCommission)
                .orderGrossValue(orderGrossValue)
                .referrerCommissionEarned(result.getTotalCommission())
                .commissionRulesApplied(toJson(result.getLineItems()))
                .build();
        bookingEventRepository.save(event);

        maybeNotifyMilestone(attribution.getReferrerId(), bookingNumber);
    }

    /** Fires a milestone notification the moment the referrer crosses the repeat-customer target. */
    private void maybeNotifyMilestone(String referrerId, int bookingNumberForThisEvent) {
        // Only a customer's 2nd booking can newly create a "repeat customer".
        if (bookingNumberForThisEvent != 2) return;
        long repeat = bookingEventRepository.findRepeatAttributionIds(referrerId).size();
        if (repeat == milestoneTarget) {
            try {
                Referrer referrer = referrerRepository.findById(referrerId).orElse(null);
                if (referrer != null && referrer.getEmail() != null) {
                    notificationService.notifyBookingStatusChange(referrer.getEmail(), referrerId,
                            "MILESTONE", "REACHED_" + milestoneTarget + "_REPEAT_CUSTOMERS");
                }
                log.info("Referrer {} reached the {}-repeat-customer milestone", referrerId, milestoneTarget);
            } catch (Exception ex) {
                log.warn("Failed to send milestone notification for referrer {}: {}", referrerId, ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Payouts
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PayoutResponse previewPayoutCalculation(String referrerId, LocalDate from, LocalDate to) {
        requireReferrer(referrerId);
        PayoutComputation comp = computePayout(referrerId, from, to);
        ReferralPayout preview = ReferralPayout.builder()
                .id(null)
                .referrerId(referrerId)
                .periodFrom(from)
                .periodTo(to)
                .calculationBreakdown(comp.breakdownJson)
                .totalCommissionCalculated(comp.total)
                .finalPayoutAmount(comp.total)
                .status(ReferralPayoutStatus.PENDING)
                .generatedAt(LocalDateTime.now())
                .generatedBy("PREVIEW")
                .build();
        return PayoutResponse.from(preview);
    }

    @Transactional
    public PayoutResponse generatePayoutRecord(String referrerId, LocalDate from, LocalDate to, String generatedBy) {
        requireReferrer(referrerId);
        PayoutComputation comp = computePayout(referrerId, from, to);
        ReferralPayout payout = ReferralPayout.builder()
                .referrerId(referrerId)
                .periodFrom(from)
                .periodTo(to)
                .calculationBreakdown(comp.breakdownJson)
                .totalCommissionCalculated(comp.total)
                .finalPayoutAmount(comp.total)
                .status(ReferralPayoutStatus.PENDING)
                .generatedBy(generatedBy)
                .build();
        return PayoutResponse.from(payoutRepository.save(payout));
    }

    private PayoutComputation computePayout(String referrerId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay(); // inclusive of the 'to' day

        BigDecimal perBooking = bookingEventRepository
                .sumCommissionEarnedForReferrerInPeriod(referrerId, fromDt, toDt);
        if (perBooking == null) perBooking = BigDecimal.ZERO;

        List<Map<String, Object>> ruleLines = new ArrayList<>();
        BigDecimal periodAdditions = BigDecimal.ZERO;

        // Period/aggregate rules active during the window.
        List<ReferralPaymentRule> activeRules = ruleRepository.findByReferrerIdAndActiveTrue(referrerId).stream()
                .filter(r -> overlaps(r.getEffectiveFrom(), r.getEffectiveUntil(), from, to))
                .toList();

        long orderCount = bookingEventRepository.countOrdersForReferrerInPeriod(referrerId, fromDt, toDt);

        for (ReferralPaymentRule rule : activeRules) {
            if (rule.getRuleType() == ReferralRuleType.FLAT_FEE_PER_PERIOD_VOLUME
                    && rule.getFlatFeeAmount() != null && rule.getVolumeThreshold() != null
                    && orderCount >= rule.getVolumeThreshold()) {
                periodAdditions = periodAdditions.add(rule.getFlatFeeAmount());
                ruleLines.add(Map.of(
                        "ruleId", rule.getId(),
                        "ruleType", rule.getRuleType().name(),
                        "amount", rule.getFlatFeeAmount(),
                        "reason", "Volume threshold met: " + orderCount + " >= " + rule.getVolumeThreshold()));
            } else if (rule.getRuleType() == ReferralRuleType.MANUAL_OVERRIDE
                    && rule.getFlatFeeAmount() != null) {
                periodAdditions = periodAdditions.add(rule.getFlatFeeAmount());
                ruleLines.add(Map.of(
                        "ruleId", rule.getId(),
                        "ruleType", rule.getRuleType().name(),
                        "amount", rule.getFlatFeeAmount(),
                        "reason", rule.getNotes() == null ? "Manual override" : rule.getNotes()));
            }
        }

        BigDecimal total = perBooking.add(periodAdditions);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("periodFrom", from.toString());
        breakdown.put("periodTo", to.toString());
        breakdown.put("perBookingCommission", perBooking);
        breakdown.put("ordersInPeriod", orderCount);
        breakdown.put("periodRules", ruleLines);
        breakdown.put("total", total);

        return new PayoutComputation(total, toJson(breakdown));
    }

    @Transactional
    public PayoutResponse approvePayoutRecord(String payoutId, String adminId) {
        ReferralPayout payout = requirePayout(payoutId);
        if (payout.getStatus() != ReferralPayoutStatus.PENDING) {
            throw new ConflictException("Only PENDING payouts can be approved");
        }
        payout.setStatus(ReferralPayoutStatus.APPROVED);
        payout.setApprovedBy(adminId);
        payout.setApprovedAt(LocalDateTime.now());
        return PayoutResponse.from(payoutRepository.save(payout));
    }

    @Transactional
    public PayoutResponse markPayoutAsPaid(String payoutId, String adminId, String paymentReference) {
        ReferralPayout payout = requirePayout(payoutId);
        if (payout.getStatus() != ReferralPayoutStatus.APPROVED) {
            throw new ConflictException("Only APPROVED payouts can be marked as paid");
        }
        payout.setStatus(ReferralPayoutStatus.PAID);
        payout.setPaidBy(adminId);
        payout.setPaidAt(LocalDateTime.now());
        payout.setPaymentReference(paymentReference);
        return PayoutResponse.from(payoutRepository.save(payout));
    }

    @Transactional
    public PayoutResponse addManualAdjustment(String payoutId, BigDecimal amount, String reason, String adminId) {
        ReferralPayout payout = requirePayout(payoutId);
        if (payout.getStatus() != ReferralPayoutStatus.PENDING) {
            throw new ConflictException("Adjustments can only be added to PENDING payouts");
        }
        payout.setManualAdjustmentAmount(amount);
        payout.setManualAdjustmentReason(reason);
        BigDecimal base = payout.getTotalCommissionCalculated() == null
                ? BigDecimal.ZERO : payout.getTotalCommissionCalculated();
        payout.setFinalPayoutAmount(base.add(amount));
        return PayoutResponse.from(payoutRepository.save(payout));
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> getPayouts(String referrerId) {
        requireReferrer(referrerId);
        return payoutRepository.findByReferrerIdOrderByGeneratedAtDesc(referrerId).stream()
                .map(PayoutResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PendingPayoutResponse> getPendingPayouts() {
        LocalDateTime now = LocalDateTime.now();
        return payoutRepository.findByStatusOrderByGeneratedAtAsc(ReferralPayoutStatus.PENDING).stream()
                .map(p -> {
                    Referrer r = referrerRepository.findById(p.getReferrerId()).orElse(null);
                    long daysSince = ChronoUnit.DAYS.between(p.getGeneratedAt(), now);
                    return new PendingPayoutResponse(
                            p.getId(), p.getReferrerId(),
                            r == null ? null : r.getName(),
                            r == null ? null : r.getReferrerType().name(),
                            p.getPeriodFrom(), p.getPeriodTo(),
                            p.getFinalPayoutAmount(), daysSince);
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // Dashboards & milestone
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminReferrerDashboardResponse getReferrerDashboard(String referrerId) {
        requireReferrer(referrerId);
        long totalCustomers = attributionRepository.countByReferrerId(referrerId);
        long repeat = bookingEventRepository.findRepeatAttributionIds(referrerId).size();
        BigDecimal totalEarned = bookingEventRepository.sumCommissionEarnedForReferrer(referrerId);
        BigDecimal totalPaid = payoutRepository.sumFinalAmountByReferrerAndStatus(referrerId, ReferralPayoutStatus.PAID);
        BigDecimal pending = payoutRepository.sumFinalAmountByReferrerAndStatusIn(referrerId,
                List.of(ReferralPayoutStatus.PENDING, ReferralPayoutStatus.APPROVED));
        return new AdminReferrerDashboardResponse(referrerId, totalCustomers, repeat,
                MilestoneProgressResponse.of(repeat, milestoneTarget),
                nz(totalEarned), nz(totalPaid), nz(pending));
    }

    @Transactional(readOnly = true)
    public MilestoneProgressResponse getMilestoneProgress(String referrerId) {
        requireReferrer(referrerId);
        long repeat = bookingEventRepository.findRepeatAttributionIds(referrerId).size();
        return MilestoneProgressResponse.of(repeat, milestoneTarget);
    }

    @Transactional(readOnly = true)
    public List<MilestoneTrackerItemResponse> getMilestoneTracker() {
        // All Customer Acquisition Specialists and their progress.
        return referrerRepository.findByReferrerType(ReferrerType.SPECIALIST, Pageable.unpaged()).stream()
                .map(r -> {
                    long repeat = bookingEventRepository.findRepeatAttributionIds(r.getId()).size();
                    return new MilestoneTrackerItemResponse(r.getId(), r.getName(), r.getReferralCode(),
                            repeat, milestoneTarget, repeat >= milestoneTarget);
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // Customer-facing (logged-in user who is also a referrer)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MyReferralDashboardResponse getMyDashboard(String userEmail) {
        Referrer referrer = requireReferrerByEmail(userEmail);
        AdminReferrerDashboardResponse stats = getReferrerDashboard(referrer.getId());

        List<ReferralActivityResponse> recent = buildRecentActivity(referrer.getId());
        List<PayoutResponse> history = payoutRepository.findByReferrerIdOrderByGeneratedAtDesc(referrer.getId()).stream()
                .map(PayoutResponse::from).toList();

        return new MyReferralDashboardResponse(
                referrer.getReferralCode(),
                stats.totalCustomers(),
                stats.repeatCustomers(),
                stats.totalEarned(),
                stats.pendingBalance(),
                stats.milestone(),
                recent,
                history);
    }

    @Transactional(readOnly = true)
    public ShareDetailsResponse getMyShareDetails(String userEmail) {
        Referrer referrer = requireReferrerByEmail(userEmail);
        BigDecimal discountValue = null;
        String discountType = null;
        String incentiveText = "";
        if (referrer.getLinkedDiscountId() != null) {
            Discount discount = discountService.getDiscountById(referrer.getLinkedDiscountId()).orElse(null);
            if (discount != null && discount.isActive()) {
                discountValue = discount.getDiscountValue();
                discountType = discount.getDiscountType().name();
                incentiveText = describeDiscount(discount) + " ";
            }
        }
        String message = "Use my code " + referrer.getReferralCode()
                + " when you sign up on Imototo for " + incentiveText + "on your first order. "
                + "Download on Google Play: " + playStoreLink;
        return new ShareDetailsResponse(referrer.getReferralCode(), message, playStoreLink, discountValue, discountType);
    }

    private List<ReferralActivityResponse> buildRecentActivity(String referrerId) {
        // Map each event to an anonymised "Customer #N" using the attribution order.
        List<ReferralAttribution> attributions = attributionRepository.findByReferrerId(referrerId);
        attributions.sort(Comparator.comparing(ReferralAttribution::getRegisteredAt));
        Map<String, Integer> indexByAttribution = new HashMap<>();
        for (int i = 0; i < attributions.size(); i++) {
            indexByAttribution.put(attributions.get(i).getId(), i + 1);
        }
        return bookingEventRepository.findRecentForReferrer(referrerId, PageRequest.of(0, 10)).stream()
                .map(e -> new ReferralActivityResponse(
                        e.getCreatedAt(),
                        "Customer #" + indexByAttribution.getOrDefault(e.getAttributionId(), 0),
                        summariseRulesApplied(e.getCommissionRulesApplied()),
                        e.getReferrerCommissionEarned()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Referrer requireReferrer(String referrerId) {
        return referrerRepository.findById(referrerId)
                .orElseThrow(() -> new NoSuchElementException("Referrer not found"));
    }

    private Referrer requireReferrerByEmail(String email) {
        if (email == null) throw new ForbiddenOperationException("No referrer record for this account");
        return referrerRepository.findByEmailIgnoreCaseAndActiveTrue(email)
                .orElseThrow(() -> new ForbiddenOperationException("No referrer record for this account"));
    }

    private ReferralPayout requirePayout(String payoutId) {
        return payoutRepository.findById(payoutId)
                .orElseThrow(() -> new NoSuchElementException("Payout not found"));
    }

    private String generateReferralCode(String name) {
        String base = (name == null ? "REF" : name).toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (base.length() > 8) base = base.substring(0, 8);
        if (base.isEmpty()) base = "REF";
        String year = String.valueOf(LocalDate.now().getYear());
        String candidate = base + year;
        int suffix = 1;
        while (referrerRepository.existsByReferralCodeIgnoreCase(candidate)) {
            candidate = base + year + String.format("%02d", suffix++);
        }
        return candidate;
    }

    private ReferralPaymentRule buildRule(String referrerId, ReferralRuleRequest req, String adminId) {
        ReferralPaymentRule rule = new ReferralPaymentRule();
        rule.setReferrerId(referrerId);
        rule.setCreatedBy(adminId);
        rule.setActive(true);
        applyRuleFields(rule, req);
        return rule;
    }

    private void applyRuleFields(ReferralPaymentRule rule, ReferralRuleRequest req) {
        validateRule(req);
        rule.setRuleType(req.ruleType());
        rule.setPaymentFrequency(req.paymentFrequency());
        rule.setPercentageValue(req.percentageValue());
        rule.setFlatFeeAmount(req.flatFeeAmount());
        rule.setBookingNumberFrom(req.bookingNumberFrom());
        rule.setBookingNumberTo(req.bookingNumberTo());
        rule.setDaysFromRegistrationLimit(req.daysFromRegistrationLimit());
        rule.setVolumeThreshold(req.volumeThreshold());
        rule.setMilestoneBookingNumber(req.milestoneBookingNumber());
        rule.setMilestonePaymentThreshold(req.milestonePaymentThreshold());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveUntil(req.effectiveUntil());
        rule.setNotes(req.notes());
    }

    private void validateRule(ReferralRuleRequest req) {
        switch (req.ruleType()) {
            case PERCENTAGE_OF_IMOTOTO_COMMISSION, PERCENTAGE_OF_ORDER_VALUE -> {
                if (req.percentageValue() == null || req.percentageValue().signum() < 0) {
                    throw new IllegalArgumentException("percentageValue is required and must be >= 0 for " + req.ruleType());
                }
            }
            case FLAT_FEE_PER_MILESTONE -> {
                if (req.flatFeeAmount() == null || req.milestoneBookingNumber() == null) {
                    throw new IllegalArgumentException("flatFeeAmount and milestoneBookingNumber are required for FLAT_FEE_PER_MILESTONE");
                }
            }
            case FLAT_FEE_PER_PERIOD_VOLUME -> {
                if (req.flatFeeAmount() == null || req.volumeThreshold() == null) {
                    throw new IllegalArgumentException("flatFeeAmount and volumeThreshold are required for FLAT_FEE_PER_PERIOD_VOLUME");
                }
            }
            case MANUAL_OVERRIDE -> {
                if (req.flatFeeAmount() == null || req.notes() == null || req.notes().isBlank()) {
                    throw new IllegalArgumentException("flatFeeAmount and a reason (notes) are required for MANUAL_OVERRIDE");
                }
            }
        }
    }

    private void recordRuleHistory(String referrerId, String before, String after, String adminId, String reason) {
        ReferralPaymentRuleHistory history = ReferralPaymentRuleHistory.builder()
                .referrerId(referrerId)
                .changedBy(adminId)
                .previousRules(before)
                .newRules(after)
                .changeReason(reason)
                .build();
        ruleHistoryRepository.save(history);
    }

    private String serialiseRules(String referrerId) {
        return toJson(ruleRepository.findByReferrerId(referrerId).stream()
                .map(ReferralRuleResponse::from).toList());
    }

    private boolean overlaps(LocalDate effFrom, LocalDate effUntil, LocalDate from, LocalDate to) {
        if (effFrom != null && effFrom.isAfter(to)) return false;
        if (effUntil != null && effUntil.isBefore(from)) return false;
        return true;
    }

    private String describeDiscount(Discount discount) {
        if (discount.getDiscountType().name().equals("PERCENTAGE")) {
            return discount.getDiscountValue().stripTrailingZeros().toPlainString() + "% off";
        }
        return "₦" + discount.getDiscountValue().stripTrailingZeros().toPlainString() + " off";
    }

    @SuppressWarnings("unchecked")
    private String summariseRulesApplied(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, List.class);
            if (items.isEmpty()) return "";
            return items.stream()
                    .map(i -> String.valueOf(i.getOrDefault("ruleType", "")))
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        } catch (Exception ex) {
            return "";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise referral JSON", ex);
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record PayoutComputation(BigDecimal total, String breakdownJson) {
    }
}

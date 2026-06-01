package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ReferralPaymentRule;
import com.work.mautonlaundry.data.repository.ReferralPaymentRuleRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a referrer's active payment rules for a single qualifying booking
 * and returns an itemised breakdown of the commission earned.
 *
 * <p>Per-booking rule types handled here:
 * <ul>
 *   <li>{@code PERCENTAGE_OF_IMOTOTO_COMMISSION} — % of Imototo's commission on the order</li>
 *   <li>{@code PERCENTAGE_OF_ORDER_VALUE} — % of the gross order value</li>
 *   <li>{@code FLAT_FEE_PER_MILESTONE} — fixed fee when a specific lifetime booking number is reached</li>
 * </ul>
 *
 * <p>{@code FLAT_FEE_PER_PERIOD_VOLUME} and {@code MANUAL_OVERRIDE} are period/
 * aggregate rules, not per-booking, and are evaluated when a payout is generated
 * (see {@code ReferralService}).
 *
 * <p>Only rules whose effective window contains the booking date are applied,
 * so mid-period rule changes are handled correctly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralCalculationEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ReferralPaymentRuleRepository ruleRepository;

    public CalculationResult calculate(String referrerId, CalculationContext ctx) {
        List<RuleLineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        List<ReferralPaymentRule> rules = ruleRepository.findActiveRulesEffectiveOn(referrerId, ctx.getBookingDate());

        for (ReferralPaymentRule rule : rules) {
            RuleLineItem item = evaluate(rule, ctx);
            if (item != null) {
                lineItems.add(item);
                total = total.add(item.getAmount());
            }
        }

        return new CalculationResult(total.setScale(2, RoundingMode.HALF_UP), lineItems);
    }

    private RuleLineItem evaluate(ReferralPaymentRule rule, CalculationContext ctx) {
        return switch (rule.getRuleType()) {
            case PERCENTAGE_OF_IMOTOTO_COMMISSION -> {
                if (!qualifiesForBookingRange(rule, ctx) || !withinDaysLimit(rule, ctx)) yield null;
                if (rule.getPercentageValue() == null) yield null;
                BigDecimal amount = percentageOf(rule.getPercentageValue(), ctx.getImototoCommission());
                yield new RuleLineItem(rule.getId(), rule.getRuleType().name(), amount,
                        rule.getPercentageValue().stripTrailingZeros().toPlainString()
                                + "% of Imototo commission (" + ctx.getImototoCommission().toPlainString()
                                + ") on booking #" + ctx.getBookingNumber());
            }
            case PERCENTAGE_OF_ORDER_VALUE -> {
                if (!qualifiesForBookingRange(rule, ctx) || !withinDaysLimit(rule, ctx)) yield null;
                if (rule.getPercentageValue() == null) yield null;
                BigDecimal amount = percentageOf(rule.getPercentageValue(), ctx.getOrderGrossValue());
                yield new RuleLineItem(rule.getId(), rule.getRuleType().name(), amount,
                        rule.getPercentageValue().stripTrailingZeros().toPlainString()
                                + "% of order value (" + ctx.getOrderGrossValue().toPlainString()
                                + ") on booking #" + ctx.getBookingNumber());
            }
            case FLAT_FEE_PER_MILESTONE -> {
                if (rule.getMilestoneBookingNumber() == null || rule.getFlatFeeAmount() == null) yield null;
                if (ctx.getBookingNumber() != rule.getMilestoneBookingNumber().intValue()) yield null;
                yield new RuleLineItem(rule.getId(), rule.getRuleType().name(),
                        rule.getFlatFeeAmount().setScale(2, RoundingMode.HALF_UP),
                        "Flat fee for reaching booking milestone #" + rule.getMilestoneBookingNumber());
            }
            // Period/aggregate rules are not evaluated per booking.
            case FLAT_FEE_PER_PERIOD_VOLUME, MANUAL_OVERRIDE -> null;
        };
    }

    private boolean qualifiesForBookingRange(ReferralPaymentRule rule, CalculationContext ctx) {
        int n = ctx.getBookingNumber();
        if (rule.getBookingNumberFrom() != null && n < rule.getBookingNumberFrom()) return false;
        if (rule.getBookingNumberTo() != null && n > rule.getBookingNumberTo()) return false;
        return true;
    }

    private boolean withinDaysLimit(ReferralPaymentRule rule, CalculationContext ctx) {
        Integer limit = rule.getDaysFromRegistrationLimit();
        return limit == null || ctx.getDaysSinceRegistration() <= limit;
    }

    private BigDecimal percentageOf(BigDecimal percentage, BigDecimal base) {
        if (base == null) return BigDecimal.ZERO;
        return base.multiply(percentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    @Getter
    @AllArgsConstructor
    public static class CalculationContext {
        private final LocalDate bookingDate;
        private final int bookingNumber;
        private final int daysSinceRegistration;
        private final BigDecimal imototoCommission;
        private final BigDecimal orderGrossValue;
    }

    @Getter
    @AllArgsConstructor
    public static class RuleLineItem {
        private final String ruleId;
        private final String ruleType;
        private final BigDecimal amount;
        private final String reason;
    }

    @Getter
    @AllArgsConstructor
    public static class CalculationResult {
        private final BigDecimal totalCommission;
        private final List<RuleLineItem> lineItems;
    }
}

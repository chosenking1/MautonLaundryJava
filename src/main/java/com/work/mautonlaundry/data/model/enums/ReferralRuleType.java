package com.work.mautonlaundry.data.model.enums;

/**
 * The five configurable payment rule types. Type 1-3 are evaluated per booking
 * by {@code ReferralCalculationEngine}; Type 4 (period volume) and Type 5
 * (manual override) are evaluated when a payout is generated for a period.
 */
public enum ReferralRuleType {
    PERCENTAGE_OF_IMOTOTO_COMMISSION,
    PERCENTAGE_OF_ORDER_VALUE,
    FLAT_FEE_PER_MILESTONE,
    FLAT_FEE_PER_PERIOD_VOLUME,
    MANUAL_OVERRIDE
}

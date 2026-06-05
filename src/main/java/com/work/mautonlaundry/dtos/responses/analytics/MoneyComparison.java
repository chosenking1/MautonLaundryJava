package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A money metric for the current period with its previous-period value and the
 * percentage change. {@code changePercent} is null when the previous value is
 * zero (no baseline to compare against — the UI shows "new"/—).
 */
public record MoneyComparison(BigDecimal current, BigDecimal previous, Double changePercent) {

    public static MoneyComparison of(BigDecimal current, BigDecimal previous) {
        BigDecimal cur = current == null ? BigDecimal.ZERO : current;
        BigDecimal prev = previous == null ? BigDecimal.ZERO : previous;
        Double pct = (prev.signum() == 0)
                ? null
                : cur.subtract(prev)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prev, 1, RoundingMode.HALF_UP)
                    .doubleValue();
        return new MoneyComparison(cur, prev, pct);
    }
}

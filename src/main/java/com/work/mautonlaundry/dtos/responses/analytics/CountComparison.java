package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A count metric for the current period with its previous-period value and % change. */
public record CountComparison(long current, long previous, Double changePercent) {

    public static CountComparison of(long current, long previous) {
        Double pct = (previous == 0)
                ? null
                : BigDecimal.valueOf(current - previous)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                    .doubleValue();
        return new CountComparison(current, previous, pct);
    }
}

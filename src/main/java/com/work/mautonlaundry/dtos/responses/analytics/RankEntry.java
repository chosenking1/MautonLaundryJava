package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;

/** One row in a Top-N ranking (customer or CAS) with its value (naira). */
public record RankEntry(String id, String name, BigDecimal value) {
}

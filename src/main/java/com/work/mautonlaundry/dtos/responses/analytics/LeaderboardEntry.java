package com.work.mautonlaundry.dtos.responses.analytics;

import java.math.BigDecimal;

/**
 * One ranked customer in a value leaderboard. {@code value} is naira for spend/
 * earnings metrics and a count for orders-this-month (the UI formats by metric).
 */
public record LeaderboardEntry(int rank, String userId, String name, BigDecimal value) {
}

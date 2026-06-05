package com.work.mautonlaundry.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Runs at midnight Africa/Lagos and snapshots the day that just ended, so the
 * Executive Dashboard's historical/trend metrics read from pre-aggregated rows
 * rather than scanning transactional tables on every page load.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsSnapshotJob {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final AnalyticsSnapshotService snapshotService;

    @Scheduled(cron = "${app.analytics.snapshot-cron:0 0 0 * * *}", zone = "Africa/Lagos")
    public void snapshotPreviousDay() {
        LocalDate yesterday = LocalDate.now(LAGOS).minusDays(1);
        try {
            snapshotService.generateSnapshot(yesterday);
        } catch (Exception ex) {
            log.error("Failed to generate analytics snapshot for {}: {}", yesterday, ex.getMessage(), ex);
        }
    }
}

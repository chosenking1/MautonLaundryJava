package com.work.mautonlaundry.services;

import com.work.mautonlaundry.security.MakerCheckerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chases and abandons undecided maker-checker requests
 * (Permission Architecture V2, spec §8.4).
 *
 * <p>Both sweeps run in one job because the spec pairs them and they read the
 * same table on the same cadence. Every 30 minutes: the windows are measured in
 * hours and days, so this is far more often than needed -- which is the point,
 * since the cost is two indexed queries over a small table and the failure mode
 * of running late is an approval nobody chased.
 *
 * <p>Neither sweep can decide a request; escalation only widens the audience,
 * and expiry only closes the door. An approval always has a human checker
 * attached, which is what makes the audit trail meaningful.
 */
@Component
@RequiredArgsConstructor
public class MakerCheckerJob {

    private static final Logger log = LoggerFactory.getLogger(MakerCheckerJob.class);

    private final MakerCheckerService makerCheckerService;

    @Scheduled(cron = "${app.maker-checker.sweep-cron:0 */30 * * * *}", zone = "Africa/Lagos")
    public void sweep() {
        try {
            int escalated = makerCheckerService.processEscalations();
            int expired = makerCheckerService.expireStale();
            if (escalated > 0 || expired > 0) {
                log.info("Maker-checker sweep: {} escalated, {} expired", escalated, expired);
            }
        } catch (Exception e) {
            // A scheduled job that throws stops being scheduled in some setups;
            // a swept-up request is not worth losing the sweeper over.
            log.error("Maker-checker sweep failed: {}", e.getMessage(), e);
        }
    }
}

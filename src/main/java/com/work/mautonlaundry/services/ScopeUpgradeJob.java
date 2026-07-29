package com.work.mautonlaundry.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reverts lapsed temporary scope upgrades (spec §6.2/§8.3, every 15 minutes).
 *
 * <p>The sweep is not what makes an expiry correct -- ScopeFilterService checks
 * expires_at on every resolution, so a lapsed upgrade stops applying immediately
 * whether or not this has run. What the sweep adds is tidiness, an audit entry,
 * and dropping the cached set so nobody keeps the wider view for the rest of the
 * TTL.
 */
@Component
@RequiredArgsConstructor
public class ScopeUpgradeJob {

    private static final Logger log = LoggerFactory.getLogger(ScopeUpgradeJob.class);

    private final ScopeUpgradeService scopeUpgradeService;

    @Scheduled(cron = "${app.scope-upgrade.revert-cron:0 */15 * * * *}", zone = "Africa/Lagos")
    public void revertExpired() {
        try {
            int reverted = scopeUpgradeService.revertExpiredScopes();
            if (reverted > 0) {
                log.info("Reverted {} expired temporary scope upgrade(s)", reverted);
            }
        } catch (Exception e) {
            // A throwing scheduled method can stop being rescheduled; losing the
            // sweeper is worse than a late revert, which is bounded anyway.
            log.error("Scope upgrade revert sweep failed: {}", e.getMessage(), e);
        }
    }
}

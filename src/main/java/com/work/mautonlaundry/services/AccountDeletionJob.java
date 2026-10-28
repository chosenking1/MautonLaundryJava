package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AccountDeletionRequest;
import com.work.mautonlaundry.data.repository.AccountDeletionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Completes account deletions whose grace period has run out. Daily is enough:
 * the promise is "on that date", and a request is never early.
 */
@Component
@RequiredArgsConstructor
public class AccountDeletionJob {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionJob.class);

    private final AccountDeletionRequestRepository requestRepository;
    private final AccountDeletionService accountDeletionService;

    @Scheduled(cron = "${app.account-deletion.sweep-cron:0 0 3 * * *}", zone = "Africa/Lagos")
    public void completeDueDeletions() {
        for (AccountDeletionRequest due : requestRepository.findDue(LocalDateTime.now())) {
            try {
                // One transaction each (through the proxy), so one failure leaves
                // the rest to complete and is simply retried tomorrow.
                accountDeletionService.complete(due.getId());
            } catch (Exception e) {
                log.error("Account deletion {} failed; will retry next sweep", due.getId(), e);
            }
        }
    }
}

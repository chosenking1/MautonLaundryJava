package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.TermsAcceptance;
import com.work.mautonlaundry.data.repository.TermsAcceptanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which terms are current, and who has agreed to them.
 *
 * <p>The current version is configuration rather than a database row so that
 * publishing new terms is a deploy with a reviewable diff, not an untracked
 * edit to a table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TermsService {

    private final TermsAcceptanceRepository acceptanceRepository;

    @Value("${app.terms.current-version:2026-08-01}")
    private String currentVersion;

    @Value("${app.terms.url:https://imototo.com.ng/terms}")
    private String termsUrl;

    public String currentVersion() {
        return currentVersion;
    }

    public String termsUrl() {
        return termsUrl;
    }

    /**
     * Records that a user agreed to a version.
     *
     * <p>Accepting the same version twice is not a second consent, so a repeat is
     * ignored rather than duplicated -- registration retries and app reinstalls
     * would otherwise litter the record.
     */
    @Transactional
    public void record(AppUser user, String version, String source, String ipAddress) {
        String accepted = (version == null || version.isBlank()) ? currentVersion : version.trim();
        if (acceptanceRepository.existsByUserAndVersion(user, accepted)) {
            return;
        }
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setUser(user);
        acceptance.setVersion(accepted);
        acceptance.setSource(source);
        acceptance.setIpAddress(ipAddress);
        acceptanceRepository.save(acceptance);
        log.info("Terms {} accepted by {} via {}", accepted, user.getEmail(), source);
    }

    /**
     * True when this user has not yet agreed to the version now in force.
     *
     * <p>Surfaced on /me so a client can prompt for re-acceptance after the terms
     * change, instead of the change passing silently.
     */
    @Transactional(readOnly = true)
    public boolean needsAcceptance(AppUser user) {
        return user != null && !acceptanceRepository.existsByUserAndVersion(user, currentVersion);
    }
}

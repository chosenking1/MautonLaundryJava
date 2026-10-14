package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.TermsAcceptance;
import com.work.mautonlaundry.data.repository.TermsAcceptanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, String> bodyCache = new ConcurrentHashMap<>();

    @Value("${app.terms.url:https://imototo.com.ng/terms}")
    private String termsUrl;

    public String currentVersion() {
        return currentVersion;
    }

    /**
     * The full text of a version, read from classpath:terms/{version}.md.
     *
     * <p>Served by the API rather than linked to a website: the apps can then
     * render the terms with no site to maintain, and -- more importantly -- the
     * exact text tied to a version stays retrievable years later, when someone
     * disputes what they agreed to. A URL can change under you; a versioned
     * resource in the build cannot.
     *
     * <p>Cached after first read: it is a file that only changes on deploy.
     */
    public String body(String version) {
        String key = (version == null || version.isBlank()) ? currentVersion : version.trim();
        return bodyCache.computeIfAbsent(key, v -> {
            ClassPathResource resource = new ClassPathResource("terms/" + v + ".md");
            if (!resource.exists()) {
                log.warn("No terms document for version {} -- clients will get an empty body", v);
                return "";
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Could not read terms document {}: {}", v, e.getMessage());
                return "";
            }
        });
    }

    /**
     * Warns at startup if the published terms still contain bracketed
     * placeholders. Shipping "[COMPANY NAME]" to real customers would be worse
     * than shipping nothing, and this is the cheapest way to keep nagging until
     * the reviewed text replaces the draft.
     */
    @jakarta.annotation.PostConstruct
    void warnIfDraft() {
        String text = body(currentVersion);
        if (text.isEmpty()) {
            log.error("Terms version {} has no document at classpath:terms/{}.md", currentVersion, currentVersion);
        } else if (text.contains("[") && text.contains("]")) {
            log.warn("Terms version {} still contains bracketed placeholders -- it is a draft "
                    + "and must be replaced with the legally reviewed text before real customers accept it",
                    currentVersion);
        }
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

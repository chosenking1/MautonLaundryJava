package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.TermsAcceptance;
import com.work.mautonlaundry.data.repository.TermsAcceptanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consent has to be provable after the fact: which text, and when. These pin the
 * rules that make the record trustworthy.
 */
@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    private static final String CURRENT = "2026-08-01";

    @Mock private TermsAcceptanceRepository repository;
    @InjectMocks private TermsService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "currentVersion", CURRENT);
        user = new AppUser();
        user.setId("u1");
        user.setEmail("customer@example.com");
    }

    @Test
    void recordsTheVersionTheUserWasActuallyShown() {
        // Not the current one: if an older build showed older text, that is what
        // they agreed to, and the record must say so.
        when(repository.existsByUserAndVersion(user, "2026-01-01")).thenReturn(false);

        service.record(user, "2026-01-01", "REGISTRATION", "10.0.0.1");

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getVersion()).isEqualTo("2026-01-01");
        assertThat(saved.getValue().getSource()).isEqualTo("REGISTRATION");
        assertThat(saved.getValue().getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void aMissingVersionFallsBackToTheOneInForce() {
        // An older client that sends nothing still gets consent recorded rather
        // than being refused an account.
        when(repository.existsByUserAndVersion(user, CURRENT)).thenReturn(false);

        service.record(user, null, "REGISTRATION", null);

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getVersion()).isEqualTo(CURRENT);
    }

    @Test
    void acceptingTheSameVersionTwiceIsNotASecondConsent() {
        // Registration retries and reinstalls must not litter the record.
        when(repository.existsByUserAndVersion(user, CURRENT)).thenReturn(true);

        service.record(user, CURRENT, "REGISTRATION", null);

        verify(repository, never()).save(any());
    }

    @Test
    void reAcceptanceIsRequiredOnlyWhenTheTermsHaveMoved() {
        when(repository.existsByUserAndVersion(user, CURRENT)).thenReturn(true);
        assertThat(service.needsAcceptance(user)).isFalse();

        when(repository.existsByUserAndVersion(eq(user), eq(CURRENT))).thenReturn(false);
        assertThat(service.needsAcceptance(user)).isTrue();
    }

    @Test
    void anAbsentUserNeverNeedsAcceptance() {
        assertThat(service.needsAcceptance(null)).isFalse();
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.VerificationToken;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The welcome email fires from inside verifyEmail, which is reachable from three
 * controllers and can be called again with a second still-valid token. These pin
 * the two things that decide whether a real person gets a duplicate: that the
 * already-verified flag is read <em>before</em> it is overwritten, and that a
 * token which fails validation sends nothing at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WelcomeEmailOnVerificationTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private CacheManager cacheManager;
    @Mock private Cache usersCache;

    @InjectMocks private UserServiceImpl userService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        when(cacheManager.getCache("users")).thenReturn(usersCache);
        user = new AppUser();
        user.setEmail("Ada@Example.com");
        user.setFull_name("Ada Obi");
        user.setEmailVerified(false);
    }

    private VerificationToken tokenFor(AppUser owner) {
        VerificationToken token = new VerificationToken();
        token.setToken("tok-1");
        token.setUser(owner);
        token.setUsed(false);
        token.setExpiryDate(LocalDateTime.now().plusHours(1));
        return token;
    }

    @Test
    void firstVerificationWelcomesTheUserByFirstName() {
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(tokenFor(user)));

        assertThat(userService.verifyEmail("tok-1")).isTrue();

        verify(emailService).sendWelcomeEmail("Ada@Example.com", "Ada");
    }

    @Test
    void anAlreadyVerifiedUserIsNotWelcomedAgain() {
        // Two verification links can be live at once; clicking the older one after
        // the account is confirmed still returns true, and used to be
        // indistinguishable from a first verification.
        user.setEmailVerified(true);
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(tokenFor(user)));

        assertThat(userService.verifyEmail("tok-1")).isTrue();

        verify(emailService, never()).sendWelcomeEmail(anyString(), any());
    }

    @Test
    void aUserWithNoNameStillGetsWelcomed() {
        user.setFull_name(null);
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(tokenFor(user)));

        assertThat(userService.verifyEmail("tok-1")).isTrue();

        verify(emailService).sendWelcomeEmail(eq("Ada@Example.com"), eq(null));
    }

    @Test
    void anExpiredTokenSendsNothing() {
        VerificationToken expired = tokenFor(user);
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(expired));

        assertThat(userService.verifyEmail("tok-1")).isFalse();

        verify(emailService, never()).sendWelcomeEmail(anyString(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void aUsedTokenSendsNothing() {
        VerificationToken used = tokenFor(user);
        used.setUsed(true);
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(used));

        assertThat(userService.verifyEmail("tok-1")).isFalse();

        verify(emailService, never()).sendWelcomeEmail(anyString(), any());
    }

    @Test
    void anUnknownTokenSendsNothing() {
        when(tokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThat(userService.verifyEmail("nope")).isFalse();

        verify(emailService, never()).sendWelcomeEmail(anyString(), any());
    }

    @Test
    void verificationStillPersistsAlongsideTheWelcome() {
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(tokenFor(user)));

        userService.verifyEmail("tok-1");

        assertThat(user.getEmailVerified()).isTrue();
        verify(userRepository, times(1)).save(user);
        verify(usersCache).evict("ada@example.com");
    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AccountDeletionRequest;
import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.repository.AccountDeletionRequestRepository;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.VerificationTokenRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.util.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Customer account deletion, for Google Play's account-deletion requirement and
 * the NDPA right to erasure.
 *
 * <h2>Shape</h2>
 * <ol>
 *   <li><b>Request</b> -- from the app or the public web page, always proven by
 *       the account password. Refused while an order is in progress: we would
 *       be holding someone's clothes with no way left to return them.</li>
 *   <li><b>Grace period</b> ({@code app.account-deletion.grace-days}, 30) -- the
 *       account is locked: JwtAuthenticationFilter refuses its tokens, so every
 *       signed-in device drops out. Logging in again cancels the request.</li>
 *   <li><b>Anonymise</b> -- AccountDeletionJob scrubs every personal field.
 *       Orders and payments stay, detached from any person, because tax and
 *       accounting records must be kept; that is what the privacy policy
 *       promises.</li>
 * </ol>
 *
 * <p>Anonymise rather than delete rows: bookings, payments, referral events and
 * support tickets all hold foreign keys to users, and a hard delete would
 * either cascade those records away or fail.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** Statuses that mean we no longer hold anything of theirs. */
    static final Set<BookingStatus> FINISHED =
            EnumSet.of(BookingStatus.DELIVERED, BookingStatus.COMPLETED, BookingStatus.CANCELLED);

    static final String DELETED_NAME = "Deleted user";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final AccountDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final AddressRepository addressRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final EmailService emailService;
    private final CacheManager cacheManager;
    private final int graceDays;

    public AccountDeletionService(AccountDeletionRequestRepository requestRepository,
                                  UserRepository userRepository,
                                  BookingRepository bookingRepository,
                                  AddressRepository addressRepository,
                                  VerificationTokenRepository tokenRepository,
                                  PasswordEncoder passwordEncoder,
                                  TokenGenerator tokenGenerator,
                                  EmailService emailService,
                                  CacheManager cacheManager,
                                  @Value("${app.account-deletion.grace-days:30}") int graceDays) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.addressRepository = addressRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.emailService = emailService;
        this.cacheManager = cacheManager;
        this.graceDays = graceDays;
    }

    // ---- request ----

    /** In-app: the caller is already signed in; the password re-confirms it is really them. */
    @Transactional
    public AccountDeletionRequest requestForUser(AppUser user, String password) {
        if (!passwordMatches(user, password)) {
            // 400, not 401: the app treats 401 as "signed out" and would drop the
            // session over a typo.
            throw new IllegalArgumentException("That password isn't right.");
        }
        return schedule(user, AccountDeletionRequest.Source.APP);
    }

    /** Public web page: no session, so email + password is the whole proof. */
    @Transactional
    public AccountDeletionRequest requestWithCredentials(String email, String password) {
        AppUser user = email == null ? null : userRepository.findUserByEmail(email.trim()).orElse(null);
        // One message for unknown email and wrong password, so the page cannot
        // be used to find out who has an account.
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !passwordMatches(user, password)) {
            throw new IllegalArgumentException("Email or password is incorrect.");
        }
        return schedule(user, AccountDeletionRequest.Source.WEB);
    }

    private AccountDeletionRequest schedule(AppUser user, AccountDeletionRequest.Source source) {
        if (!user.hasRole("USER")) {
            // Riders and laundry agents have earnings and payouts to settle; their
            // deletion needs its own rules and is handled by the team for now.
            throw new ConflictException("Staff and agent accounts can't be deleted here. "
                    + "Please contact support and we'll close it for you.");
        }
        Optional<AccountDeletionRequest> existing = requestRepository.findPendingForUser(user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (hasOrderInProgress(user)) {
            throw new ConflictException("You have an order in progress. Once it's delivered "
                    + "you can delete your account.");
        }

        LocalDateTime now = LocalDateTime.now();
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setUser(user);
        request.setSource(source);
        request.setRequestedAt(now);
        request.setScheduledFor(now.plusDays(graceDays));
        requestRepository.save(request);

        String email = user.getEmail();
        String firstName = DeliveryService.firstNameOf(user);
        String date = request.getScheduledFor().format(DATE);
        afterCommit(() -> emailService.sendAccountDeletionScheduled(email, firstName, date));
        log.info("Account deletion scheduled for user {} via {} on {}", user.getId(), source, date);
        return request;
    }

    private boolean hasOrderInProgress(AppUser user) {
        return bookingRepository.existsByUserAndDeletedFalseAndStatusNotIn(user, FINISHED);
    }

    private boolean passwordMatches(AppUser user, String password) {
        return password != null && !password.isEmpty()
                && passwordEncoder.matches(password, user.getPassword());
    }

    // ---- grace period ----

    /** JwtAuthenticationFilter: a pending account's tokens are refused for the whole grace period. */
    @Transactional(readOnly = true)
    public boolean isPendingDeletion(String email) {
        return email != null && requestRepository.isPendingForEmail(email);
    }

    /**
     * Login during the grace period cancels the request. Called after the
     * password has been checked, so a failed attempt cancels nothing.
     *
     * @return true when a pending request was cancelled
     */
    @Transactional
    public boolean cancelOnLogin(AppUser user) {
        Optional<AccountDeletionRequest> pending = requestRepository.findPendingForUser(user.getId());
        if (pending.isEmpty()) {
            return false;
        }
        pending.get().setCancelledAt(LocalDateTime.now());
        requestRepository.save(pending.get());

        String email = user.getEmail();
        String firstName = DeliveryService.firstNameOf(user);
        afterCommit(() -> emailService.sendAccountDeletionCancelled(email, firstName));
        log.info("Account deletion cancelled by login for user {}", user.getId());
        return true;
    }

    // ---- anonymise ----

    /**
     * Completes one due request. Run per request in its own transaction (see
     * AccountDeletionJob), so one bad row cannot roll back the others.
     */
    @Transactional
    public void complete(Long requestId) {
        AccountDeletionRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getCancelledAt() != null || request.getCompletedAt() != null) {
            return;
        }
        AppUser user = request.getUser();

        // Re-checked: the lock should make a new order impossible, but if one
        // exists, wiping its customer's phone number would strand the rider.
        // Try again tomorrow rather than guess.
        if (hasOrderInProgress(user)) {
            log.warn("Account deletion {} deferred: user {} has an order in progress", requestId, user.getId());
            return;
        }

        String originalEmail = user.getEmail();
        String firstName = DeliveryService.firstNameOf(user);
        anonymise(user);
        request.setCompletedAt(LocalDateTime.now());
        requestRepository.save(request);

        // The last thing we send to that address -- after commit, so it is only
        // sent if the data really is gone.
        afterCommit(() -> emailService.sendAccountDeleted(originalEmail, firstName));
        log.info("Account deletion {} completed for user {}", requestId, user.getId());
    }

    private void anonymise(AppUser user) {
        String oldEmailKey = user.getEmail() == null ? null : user.getEmail().toLowerCase();

        user.setFull_name(DELETED_NAME);
        // Unique and non-null by schema, and must never be deliverable.
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setPhone_number(null);
        // Random and discarded: nobody, including us, can sign in as this user.
        user.setPassword(passwordEncoder.encode(tokenGenerator.generateSecureToken()));
        user.setEmailVerified(false);
        user.setDeleted(true);
        userRepository.save(user);

        java.util.List<Address> addresses = addressRepository.findByUser(user);
        for (Address address : addresses) {
            // Kept as rows because past bookings point at them; emptied because
            // a home address and its coordinates are exactly what we promised to
            // remove.
            address.setStreet("Removed");
            address.setStreet_number(null);
            address.setCity(null);
            address.setState(null);
            address.setZip(null);
            address.setLatitude(null);
            address.setLongitude(null);
            address.setDeleted(true);
            address.setIsDefault(false);
        }
        addressRepository.saveAll(addresses);

        tokenRepository.deleteByUserId(user.getId());

        Cache users = cacheManager.getCache("users");
        if (users != null) {
            if (oldEmailKey != null) users.evict(oldEmailKey);
            users.evict(user.getId());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

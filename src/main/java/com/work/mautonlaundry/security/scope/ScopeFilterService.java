package com.work.mautonlaundry.security.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.TemporaryScopeGrant;
import com.work.mautonlaundry.data.model.UserScope;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import com.work.mautonlaundry.data.repository.TemporaryScopeGrantRepository;
import com.work.mautonlaundry.data.repository.UserScopeRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the data-scope filter applied to scoped queries
 * (Permission Architecture V2, spec §5.4).
 *
 * <p>Scope is the third, independent dimension: a request passes only if the
 * user has the permission AND their scope covers the data. Holding
 * ORDER_VIEW_ALL does not widen scope -- a Lagos State Head still sees only
 * Lagos orders.
 *
 * <h2>Deviation: scope is read from the database, not the JWT</h2>
 *
 * Spec §5.4 says to read scopeLevel/scopeValue from JWT claims. That is unsafe
 * in this codebase, and the spec contradicts itself on it:
 *
 * <ul>
 *   <li>Tokens here last <b>7 days</b>
 *       ({@code app.jwt-expiration-milliseconds=604800000}) and there is
 *       <b>no refresh token, no revocation and no blacklist</b>. A scope in the
 *       token is therefore frozen for up to a week.</li>
 *   <li>Spec §6.1 requires a temporary scope upgrade to revert automatically
 *       when its expiry passes, "enforced by a scheduled job (not by the user
 *       returning the access)". With scope in the JWT the job can only flip a
 *       database row the filter never reads -- a 2-hour upgrade would in
 *       practice last until the token expired, up to 7 days.</li>
 *   <li>Spec §8.3 has revertExpiredScopes() "trigger JWT invalidation for
 *       affected users". There is no such mechanism to trigger.</li>
 * </ul>
 *
 * So scope is resolved per request from user_scope and cached in Redis for five
 * minutes -- the same shape as
 * {@link com.work.mautonlaundry.security.PermissionEvaluationService}. A change
 * then takes effect on the next request after {@link #invalidate}, and temporary
 * scope expiry works as §6 describes without any token changes.
 *
 * <h2>Fail closed</h2>
 *
 * A user with no user_scope row sees <b>nothing</b>, not everything. Note what
 * that means at cutover: nobody has a scope row yet, so wiring this into a
 * repository before seeding scopes would blank every list in the product. The
 * seeding migration is a prerequisite, not a follow-up.
 *
 * <h2>Honest limit on "not bypassable"</h2>
 *
 * Spec §5.1 says the filter "is not optional and not bypassable by the calling
 * code". A {@link Specification} cannot deliver that: it only applies where a
 * caller remembers to pass it, and a plain {@code findAll()} still returns
 * everything. Truly non-bypassable filtering needs Hibernate {@code @Filter} on
 * the entities, enabled per session. This class implements the spec's
 * prescribed mechanism; the claim in §5.1 is aspirational until that lands, and
 * every scoped call site must be audited rather than trusted.
 *
 * <p><b>Nothing calls this yet</b> -- built and tested alongside the live paths,
 * like PermissionEvaluationService.
 */
@Service
@RequiredArgsConstructor
public class ScopeFilterService {

    private static final Logger log = LoggerFactory.getLogger(ScopeFilterService.class);

    /**
     * Also the ceiling on how long an EXPIRED temporary upgrade can linger in a
     * cached set (spec §6). ScopeUpgradeService invalidates on revert, so this
     * is the backstop, not the mechanism.
     */
    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_KEY_PREFIX = "scope:v1:";

    private final UserScopeRepository userScopeRepository;
    private final TemporaryScopeGrantRepository temporaryScopeGrantRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- resolution ----

    /** The current authenticated user's scope; denies when unauthenticated. */
    public ScopeContext currentScope() {
        String userId = SecurityUtil.getCurrentUserId();
        return userId == null ? ScopeContext.denyAll() : scopeFor(userId);
    }

    public ScopeContext scopeFor(String userId) {
        if (userId == null || userId.isBlank()) {
            return ScopeContext.denyAll();
        }
        ScopeContext cached = readCache(userId);
        if (cached != null) {
            return cached;
        }
        ScopeContext resolved = resolveFromDatabase(userId);
        writeCache(userId, resolved);
        return resolved;
    }

    private ScopeContext resolveFromDatabase(String userId) {
        // A live temporary upgrade wins over the permanent scope (spec §6.1).
        // This is the whole reason scope is read from the database rather than
        // the JWT: an expiry can actually take effect.
        Optional<TemporaryScopeGrant> upgrade =
                temporaryScopeGrantRepository.findActiveFor(userId, LocalDateTime.now());
        if (upgrade.isPresent()) {
            TemporaryScopeGrant g = upgrade.get();
            return switch (g.getTemporaryScopeLevel()) {
                case NATIONAL -> ScopeContext.national();
                case REGIONAL -> ScopeContext.states(ScopeLevel.REGIONAL,
                        new HashSet<>(userScopeRepository.findStateIdsByRegionId(g.getTemporaryRegionId())));
                case STATE -> ScopeContext.states(ScopeLevel.STATE,
                        g.getTemporaryStateId() == null ? Set.of() : Set.of(g.getTemporaryStateId()));
                case ZONE -> ScopeContext.zone(g.getTemporaryLgaId());
                // V22's CHECK forbids a SPECIALIST upgrade -- nobody requests a
                // temporary narrowing -- so this is unreachable, not a fallthrough.
                case SPECIALIST -> ScopeContext.denyAll();
            };
        }

        Optional<UserScope> found = userScopeRepository.findById(userId);
        if (found.isEmpty()) {
            // Closed by default: no scope assigned means no data, never all data.
            return ScopeContext.denyAll();
        }
        UserScope scope = found.get();

        return switch (scope.getScopeLevel()) {
            case NATIONAL -> ScopeContext.national();
            case REGIONAL -> {
                // Flatten the region to its states now, so the query is a simple
                // IN rather than a subquery.
                List<Integer> stateIds =
                        userScopeRepository.findStateIdsByRegionId(scope.getRegionId());
                yield ScopeContext.states(
                        scope.getScopeLevel(), new HashSet<>(stateIds));
            }
            case STATE -> ScopeContext.states(
                    scope.getScopeLevel(),
                    scope.getStateId() == null ? Set.of() : Set.of(scope.getStateId()));
            case ZONE -> ScopeContext.zone(scope.getLgaId());
            case SPECIALIST -> ScopeContext.specialist(scope.getSpecialistUserId());
        };
    }

    /**
     * Drops a user's cached scope. Must be called on any scope change, and by
     * the temporary-scope expiry job -- otherwise a revoked scope survives for
     * up to the TTL.
     */
    public void invalidate(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Could not invalidate scope cache for user {}: {}", userId, e.getMessage());
        }
    }

    // ---- specifications ----

    /**
     * The scope filter for a scoped entity, to be AND-ed onto the query.
     *
     * <pre>
     *   Specification&lt;Booking&gt; scope = scopeFilterService.getScope(Booking.class);
     *   bookingRepository.findAll(scope.and(otherCriteria), pageable);
     * </pre>
     *
     * @throws IllegalArgumentException for an entity with no scope rule. This is
     *         deliberate: silently returning "no restriction" for an unmapped
     *         entity would turn a missing rule into a data leak, and the throw
     *         surfaces it the first time anyone tries.
     */
    public <T> Specification<T> getScope(Class<T> entityClass) {
        return specificationFor(entityClass, currentScope());
    }

    /**
     * The filter for an explicit scope, independent of who is authenticated.
     *
     * <p>Separated from {@link #getScope} because that one reaches for the
     * current user through the static SecurityUtil, which makes the rule itself
     * impossible to exercise without a SecurityContext. The decision belongs in
     * a pure function; resolving whose scope it is does not.
     */
    @SuppressWarnings("unchecked")
    public <T> Specification<T> specificationFor(Class<T> entityClass, ScopeContext scope) {
        if (scope.denied()) {
            return (root, query, cb) -> cb.disjunction();  // always false
        }
        if (scope.isUnrestricted()) {
            return (root, query, cb) -> cb.conjunction();  // always true
        }

        // The casts are safe: each branch is guarded by the matching
        // entityClass check, so T is that entity. Generics cannot express that.
        if (Booking.class.equals(entityClass)) {
            return (root, query, cb) ->
                    bookingPredicate((Root<Booking>) (Root<?>) root, query, cb, scope);
        }
        if (Address.class.equals(entityClass)) {
            return (root, query, cb) ->
                    addressPredicate((Root<Address>) (Root<?>) root, cb, scope);
        }

        throw new IllegalArgumentException(
                "No scope rule defined for " + entityClass.getSimpleName()
                        + ". Add one before querying it under scope -- refusing to "
                        + "default to unrestricted.");
    }

    /**
     * Orders are scoped by their pickup address (spec §5.3: "Orders -- scoped by
     * State + Zone"), which is the only geography a booking has.
     *
     * <p>SPECIALIST scope is different in kind: it is not geographic but
     * "their own orders" (spec §5.2), which for a laundry specialist means the
     * bookings assigned to them.
     */
    private Predicate bookingPredicate(
            Root<Booking> root,
            CommonAbstractCriteria query,
            CriteriaBuilder cb,
            ScopeContext scope) {

        if (scope.level() == ScopeLevel.SPECIALIST) {
            // EXISTS rather than a join: a booking may have several assignment
            // rows, and a join would duplicate it across pages.
            Subquery<Long> sub = query.subquery(Long.class);
            Root<LaundrymanAssignment> assignment = sub.from(LaundrymanAssignment.class);
            sub.select(cb.literal(1L))
                    .where(cb.and(
                            cb.equal(assignment.get("booking").get("id"), root.get("id")),
                            cb.equal(assignment.get("laundryman").get("id"), scope.specialistUserId())
                    ));
            return cb.exists(sub);
        }

        // LEFT join so the null check below is reachable: an unresolved address
        // must be excluded, not silently matched.
        Join<Booking, Address> address = root.join("pickupAddress", JoinType.LEFT);

        if (scope.lgaId() != null) {
            return cb.equal(address.get("lgaId"), scope.lgaId());
        }
        if (!scope.stateIds().isEmpty()) {
            return address.get("stateId").in(scope.stateIds());
        }
        return cb.disjunction();
    }

    private Predicate addressPredicate(
            Root<Address> root,
            CriteriaBuilder cb,
            ScopeContext scope) {

        if (scope.level() == ScopeLevel.SPECIALIST) {
            // A specialist has no geographic claim over addresses; only their
            // own.
            return cb.equal(root.get("user").get("id"), scope.specialistUserId());
        }
        if (scope.lgaId() != null) {
            return cb.equal(root.get("lgaId"), scope.lgaId());
        }
        if (!scope.stateIds().isEmpty()) {
            return root.get("stateId").in(scope.stateIds());
        }
        return cb.disjunction();
    }

    // ---- cache ----

    private ScopeContext readCache(String userId) {
        try {
            String raw = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + userId);
            return raw == null ? null : objectMapper.readValue(raw, ScopeContext.class);
        } catch (Exception e) {
            // Redis down or a stale encoding degrades to a database read. It
            // cannot widen access: the database is authoritative.
            log.warn("Scope cache read failed for user {}, falling back to database: {}",
                    userId, e.getMessage());
            return null;
        }
    }

    private void writeCache(String userId, ScopeContext scope) {
        try {
            redisTemplate.opsForValue()
                    .set(CACHE_KEY_PREFIX + userId, objectMapper.writeValueAsString(scope), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Scope cache write failed for user {}: {}", userId, e.getMessage());
        }
    }
}

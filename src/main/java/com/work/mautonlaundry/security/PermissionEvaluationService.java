package com.work.mautonlaundry.security;

import com.work.mautonlaundry.data.repository.PermissionRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single source of truth for "does this user have this permission?"
 * (Permission Architecture V2, spec §8.1).
 *
 * <p><b>Closed by default.</b> A permission is held only if some row says so.
 * A missing row is a denial, never an allowance. This is the rule the outgoing
 * {@link com.work.mautonlaundry.services.DynamicPermissionService} inverts --
 * it returns {@code true} for any endpoint absent from the permission table.
 *
 * <p><b>Evaluation order</b> (spec §8.1) resolves to set arithmetic:
 * <pre>
 *   effective = (userGrants ∪ roleGrants ∪ roleModuleGrants ∪ userModuleGrants)
 *               − userDenies
 * </pre>
 * Module exclusions are already subtracted per assignment inside the grant query
 * (see {@link PermissionRepository#findGrantedPermissionNames}), so subtracting
 * user-level denies last is what makes "explicit DENY wins" hold against every
 * grant source at once.
 *
 * <p>Spec §2.6 and §8.1 disagree in one place: §2.6 step 3 says a role's direct
 * grant can be blocked by "an exclusion on their user record", but exclusions
 * only exist attached to a module assignment, so a module-scoped carve-out would
 * leak into an unrelated role grant. §8.1's reading is implemented here: an
 * exclusion only constrains the module assignment it hangs off.
 *
 * <p><b>Nothing calls this yet.</b> It is built and tested alongside the live
 * permission path; the 66 existing {@code @PreAuthorize} annotations still
 * decide access until the cutover step.
 */
@Service
@RequiredArgsConstructor
public class PermissionEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PermissionEvaluationService.class);

    /**
     * Spec §8.1. The TTL is the ceiling on how long a revoked permission can
     * still be honoured if an explicit invalidation is ever missed, so it is a
     * backstop rather than the mechanism -- {@link #invalidate} is.
     */
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Versioned so a change to the encoding can never read a stale value written
     * by an older deploy -- bump the version instead of reasoning about overlap.
     */
    private static final String CACHE_KEY_PREFIX = "perms:v1:";

    private static final String DELIMITER = "\n";

    private final PermissionRepository permissionRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Every permission key the user effectively holds. Empty for an unknown or
     * null user -- closed by default.
     */
    public Set<String> getEffectivePermissions(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }

        Set<String> cached = readCache(userId);
        if (cached != null) {
            return cached;
        }

        Set<String> effective = resolveFromDatabase(userId);
        writeCache(userId, effective);
        return effective;
    }

    public boolean hasPermission(String userId, String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        return getEffectivePermissions(userId).contains(permissionKey);
    }

    /**
     * Entry point for {@code @PreAuthorize} expressions, e.g.
     * {@code @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PAYOUT_SETTLE')")}.
     * Denies when there is no authenticated user.
     */
    public boolean currentUserHasPermission(String permissionKey) {
        return hasPermission(SecurityUtil.getCurrentUserId(), permissionKey);
    }

    /**
     * Drops a user's cached set. Spec §8.1 requires this on any change to that
     * user's permissions, modules or role -- callers land in a later step, as
     * nothing mutates these tables yet.
     */
    public void invalidate(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            // A failed invalidation must not fail the mutation that triggered
            // it; the TTL bounds the staleness.
            log.warn("Could not invalidate permission cache for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Drops every cached permission set.
     *
     * <p>Called at startup. Redis outlives a deploy, so without this an admin
     * whose set was cached before the deploy keeps that stale set for up to the
     * TTL — and a deploy that introduces a permission an endpoint now demands
     * would lock them out for those five minutes. Flushing on boot makes the new
     * grants take effect on the first request instead.
     *
     * @return how many keys were dropped, for the startup log
     */
    public long invalidateAll() {
        try {
            Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long deleted = redisTemplate.delete(keys);
            return deleted == null ? 0 : deleted;
        } catch (Exception e) {
            // Startup must not fail because Redis is unreachable; the TTL still
            // bounds the staleness.
            log.warn("Could not flush the permission cache: {}", e.getMessage());
            return 0;
        }
    }

    private Set<String> resolveFromDatabase(String userId) {
        List<String> granted = permissionRepository.findGrantedPermissionNames(userId);
        List<String> denied = permissionRepository.findDeniedPermissionNames(userId);

        Set<String> effective = new HashSet<>(granted);
        effective.removeAll(denied);
        return effective;
    }

    /**
     * @return the cached set, or null when absent -- distinguishing "not cached"
     *         from a genuinely cached empty set, which is a real state for a
     *         user who holds nothing.
     */
    private Set<String> readCache(String userId) {
        try {
            String raw = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + userId);
            return raw == null ? null : decode(raw);
        } catch (Exception e) {
            // Redis being down degrades to a database read rather than an
            // outage. It cannot widen access: the database is authoritative.
            log.warn("Permission cache read failed for user {}, falling back to database: {}",
                    userId, e.getMessage());
            return null;
        }
    }

    private void writeCache(String userId, Set<String> permissions) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + userId, encode(permissions), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Permission cache write failed for user {}: {}", userId, e.getMessage());
        }
    }

    private static String encode(Set<String> permissions) {
        return String.join(DELIMITER, permissions);
    }

    private static Set<String> decode(String raw) {
        // "".split() would yield [""] -- a set holding one empty permission.
        if (raw.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(raw.split(DELIMITER)));
    }
}

package com.work.mautonlaundry.security.scope;

import com.work.mautonlaundry.data.model.enums.ScopeLevel;

import java.util.Set;

/**
 * A user's scope, resolved and flattened into what a query actually needs.
 *
 * <p>REGIONAL is expanded to its member states at resolution time, so REGIONAL
 * and STATE both reduce to "state in this set". The region-to-states lookup then
 * happens once per cache period rather than as a subquery on every scoped query.
 *
 * <p>{@link #denyAll()} is the value for a user with no scope row. It is
 * deliberately a real state rather than null: a missing scope must mean "sees
 * nothing", and a null would invite callers to skip filtering entirely.
 */
public record ScopeContext(
        ScopeLevel level,
        /** Populated for REGIONAL (member states) and STATE (one). */
        Set<Integer> stateIds,
        /**
         * Populated for ZONE: the LGAs of the operational zone. A zone is one or
         * more LGAs grouped together (an LGA may be a standalone zone), so like
         * REGIONAL this reduces to "lga in this set" -- a single-LGA zone is just
         * a set of one.
         */
        Set<Integer> lgaIds,
        /** Populated for SPECIALIST. */
        String specialistUserId,
        /** True when the user has no scope assigned: see nothing. */
        boolean denied
) {

    public static ScopeContext national() {
        return new ScopeContext(ScopeLevel.NATIONAL, Set.of(), Set.of(), null, false);
    }

    public static ScopeContext states(ScopeLevel level, Set<Integer> stateIds) {
        // A REGIONAL scope over a region with no states would otherwise become
        // an unrestricted "IN ()" -- deny instead.
        if (stateIds == null || stateIds.isEmpty()) {
            return denyAll();
        }
        return new ScopeContext(level, Set.copyOf(stateIds), Set.of(), null, false);
    }

    public static ScopeContext zone(Set<Integer> lgaIds) {
        // A zone with no LGAs would become an unrestricted "IN ()" -- deny.
        if (lgaIds == null || lgaIds.isEmpty()) {
            return denyAll();
        }
        return new ScopeContext(ScopeLevel.ZONE, Set.of(), Set.copyOf(lgaIds), null, false);
    }

    public static ScopeContext specialist(String specialistUserId) {
        if (specialistUserId == null || specialistUserId.isBlank()) {
            return denyAll();
        }
        return new ScopeContext(ScopeLevel.SPECIALIST, Set.of(), Set.of(), specialistUserId, false);
    }

    /** No scope assigned, or an unresolvable one: sees nothing. */
    public static ScopeContext denyAll() {
        return new ScopeContext(null, Set.of(), Set.of(), null, true);
    }

    /** True when the scope imposes no restriction at all. */
    public boolean isUnrestricted() {
        return !denied && level == ScopeLevel.NATIONAL;
    }
}

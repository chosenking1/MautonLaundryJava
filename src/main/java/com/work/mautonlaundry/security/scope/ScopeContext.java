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
        /** Populated for ZONE. */
        Integer lgaId,
        /** Populated for SPECIALIST. */
        String specialistUserId,
        /** True when the user has no scope assigned: see nothing. */
        boolean denied
) {

    public static ScopeContext national() {
        return new ScopeContext(ScopeLevel.NATIONAL, Set.of(), null, null, false);
    }

    public static ScopeContext states(ScopeLevel level, Set<Integer> stateIds) {
        // A REGIONAL scope over a region with no states would otherwise become
        // an unrestricted "IN ()" -- deny instead.
        if (stateIds == null || stateIds.isEmpty()) {
            return denyAll();
        }
        return new ScopeContext(level, Set.copyOf(stateIds), null, null, false);
    }

    public static ScopeContext zone(Integer lgaId) {
        if (lgaId == null) {
            return denyAll();
        }
        return new ScopeContext(ScopeLevel.ZONE, Set.of(), lgaId, null, false);
    }

    public static ScopeContext specialist(String specialistUserId) {
        if (specialistUserId == null || specialistUserId.isBlank()) {
            return denyAll();
        }
        return new ScopeContext(ScopeLevel.SPECIALIST, Set.of(), null, specialistUserId, false);
    }

    /** No scope assigned, or an unresolvable one: sees nothing. */
    public static ScopeContext denyAll() {
        return new ScopeContext(null, Set.of(), null, null, true);
    }

    /** True when the scope imposes no restriction at all. */
    public boolean isUnrestricted() {
        return !denied && level == ScopeLevel.NATIONAL;
    }
}

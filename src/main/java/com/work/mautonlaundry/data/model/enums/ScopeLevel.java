package com.work.mautonlaundry.data.model.enums;

/**
 * How much data a user can see, independent of what actions they may perform
 * (Permission Architecture V2, spec §5).
 *
 * <p>Scope is orthogonal to permissions: holding ORDER_VIEW_ALL does not widen
 * scope. A Lagos State Head with ORDER_VIEW_ALL still sees only Lagos orders.
 *
 * <p>Hierarchy (spec §5.1): NATIONAL &gt; REGIONAL &gt; STATE &gt; ZONE &gt; SPECIALIST.
 * Mapped onto Nigeria's administrative structure:
 * <pre>
 *   NATIONAL    all data
 *   REGIONAL    a geopolitical zone, e.g. South-West (a named group of states)
 *   STATE       e.g. Lagos
 *   ZONE        a Local Government Area, e.g. Ikorodu
 *   SPECIALIST  only the user's own work
 * </pre>
 */
public enum ScopeLevel {
    NATIONAL,
    REGIONAL,
    STATE,
    ZONE,
    SPECIALIST
}

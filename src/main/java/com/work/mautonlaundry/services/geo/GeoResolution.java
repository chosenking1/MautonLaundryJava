package com.work.mautonlaundry.services.geo;

/**
 * Outcome of resolving a coordinate to canonical geography.
 *
 * <p>rawState/rawLga carry exactly what the provider returned, resolved or not.
 * They are what an admin needs to see in the unresolved queue to decide whether
 * to add an alias, and what makes a wrong resolution traceable after the fact.
 */
public record GeoResolution(
        Status status,
        Integer stateId,
        Integer lgaId,
        String rawState,
        String rawLga
) {

    public enum Status {
        /** State and LGA both matched a canonical row. */
        RESOLVED,

        /**
         * The state matched but the LGA did not -- an unknown spelling, or a
         * coordinate for which the provider returned no
         * administrative_area_level_2.
         *
         * <p>Deliberately not treated as failure: the state is still recorded,
         * so STATE-scoped staff see the order. Only ZONE-scoped staff lose it,
         * and the row lands in the admin queue where one alias fixes it
         * permanently.
         */
        STATE_ONLY,

        /** Nothing usable came back, or the state itself was unrecognised. */
        UNRESOLVED,

        /** No API key configured. Distinct from UNRESOLVED: nothing was attempted. */
        DISABLED,

        /** The provider call failed (network, quota, billing, malformed reply). */
        ERROR
    }

    public static GeoResolution resolved(Integer stateId, Integer lgaId, String rawState, String rawLga) {
        return new GeoResolution(Status.RESOLVED, stateId, lgaId, rawState, rawLga);
    }

    public static GeoResolution stateOnly(Integer stateId, String rawState, String rawLga) {
        return new GeoResolution(Status.STATE_ONLY, stateId, null, rawState, rawLga);
    }

    public static GeoResolution unresolved(String rawState, String rawLga) {
        return new GeoResolution(Status.UNRESOLVED, null, null, rawState, rawLga);
    }

    public static GeoResolution disabled() {
        return new GeoResolution(Status.DISABLED, null, null, null, null);
    }

    public static GeoResolution error() {
        return new GeoResolution(Status.ERROR, null, null, null, null);
    }

    /** True when the call produced something worth persisting on the address. */
    public boolean hasState() {
        return stateId != null;
    }
}

package com.work.mautonlaundry.services.geo;

/**
 * Raised when an address is created or its coordinates change, asking for
 * server-side geography resolution.
 *
 * <p>Carries only the id. The listener re-reads the row inside its own
 * transaction rather than receiving a detached entity, so it can never persist a
 * stale copy over a concurrent edit.
 */
public record AddressGeoResolutionRequested(String addressId) {
}

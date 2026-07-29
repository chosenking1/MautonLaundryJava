package com.work.mautonlaundry.services.geo;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Applies canonical geography to addresses (Permission Architecture V2, spec §5).
 *
 * <p><b>Why this is asynchronous and after-commit.</b> Resolution calls Google
 * over the network. AddressService.createAddress is @Transactional, so resolving
 * inline would hold a database connection open for the duration of an HTTP round
 * trip -- under load that starves the pool long before Google is the problem. It
 * would also mean a Google outage, a quota block, or an expired card could fail
 * a customer's address creation, and therefore their booking. Geography is
 * metadata about the address, not a precondition for having one.
 *
 * <p>So the address commits first and resolution follows on another thread.
 * AFTER_COMMIT (rather than a bare @Async) guarantees the row is actually
 * visible when the listener reads it -- firing during the transaction would race
 * the commit and intermittently find nothing.
 *
 * <p>Consequence, accepted deliberately: an address is briefly unresolved after
 * creation. That is already the steady state for anything Google cannot place,
 * so every reader must tolerate a null lga_id regardless.
 */
@Service
@RequiredArgsConstructor
public class AddressGeoService {

    private static final Logger log = LoggerFactory.getLogger(AddressGeoService.class);

    private final AddressRepository addressRepository;
    private final GeoResolutionService geoResolutionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolutionRequested(AddressGeoResolutionRequested event) {
        try {
            resolveAndPersist(event.addressId());
        } catch (Exception e) {
            // Nothing upstream is waiting on this thread, and the address is
            // already committed. Swallow, leave geo_resolved_at null, and let
            // the backfill retry it later.
            log.warn("Geo resolution failed for address {}: {}", event.addressId(), e.getMessage());
        }
    }

    /**
     * Resolves one address and writes the outcome. Runs in its own transaction:
     * called from an after-commit listener, where the original transaction is
     * already finished.
     *
     * @return the outcome, for callers such as the backfill that report on it
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GeoResolution resolveAndPersist(String addressId) {
        Optional<Address> found = addressRepository.findById(addressId);
        if (found.isEmpty()) {
            log.warn("Geo resolution skipped: address {} no longer exists", addressId);
            return GeoResolution.unresolved(null, null);
        }
        Address address = found.get();

        GeoResolution resolution = geoResolutionService.resolve(address.getLatitude(), address.getLongitude());

        switch (resolution.status()) {
            case RESOLVED -> {
                address.setStateId(resolution.stateId());
                address.setLgaId(resolution.lgaId());
                address.setGeoResolvedAt(LocalDateTime.now());
            }
            case STATE_ONLY -> {
                // Keep the state: STATE-scoped staff can still see this order.
                // Only the zone is unknown, and an lga_aliases row fixes that
                // for every future address in the same LGA.
                address.setStateId(resolution.stateId());
                address.setLgaId(null);
                address.setGeoResolvedAt(LocalDateTime.now());
            }
            case UNRESOLVED -> {
                // Attempted and genuinely unplaceable (out of country, or a
                // state we do not stock). Stamp it so the backfill stops
                // retrying; an admin resolves it from the queue.
                address.setGeoResolvedAt(LocalDateTime.now());
            }
            case DISABLED, ERROR -> {
                // Deliberately leave geo_resolved_at null. Nothing is wrong with
                // the address -- the key is missing or Google failed -- so this
                // must remain eligible for retry rather than being written off.
                log.debug("Geo resolution not applied for address {}: {}", addressId, resolution.status());
                return resolution;
            }
        }

        addressRepository.save(address);
        return resolution;
    }

    /**
     * Resolves addresses that have never been attempted (spec §5: existing rows
     * predate canonical geography and are invisible to scoped staff until filled
     * in).
     *
     * <p>Bounded by {@code limit} and driven manually rather than on a timer:
     * every row is a billable Google call, so a runaway loop is a bill. Safe to
     * re-run -- geo_resolved_at makes it idempotent, and DISABLED/ERROR rows stay
     * eligible so a run during an outage costs nothing but retries cleanly.
     */
    public BackfillReport backfill(int limit) {
        List<Address> pending = addressRepository.findUnresolvedWithCoordinates(limit);
        int resolved = 0, stateOnly = 0, unresolved = 0, failed = 0;

        for (Address address : pending) {
            GeoResolution r;
            try {
                r = resolveAndPersist(address.getId());
            } catch (Exception e) {
                log.warn("Backfill error on address {}: {}", address.getId(), e.getMessage());
                failed++;
                continue;
            }
            switch (r.status()) {
                case RESOLVED -> resolved++;
                case STATE_ONLY -> stateOnly++;
                case UNRESOLVED -> unresolved++;
                case DISABLED, ERROR -> failed++;
            }
        }

        BackfillReport report = new BackfillReport(pending.size(), resolved, stateOnly, unresolved, failed,
                addressRepository.countUnresolvedWithCoordinates());
        log.info("Geo backfill: attempted={} resolved={} stateOnly={} unresolved={} failed={} remaining={}",
                report.attempted(), report.resolved(), report.stateOnly(), report.unresolved(),
                report.failed(), report.remaining());
        return report;
    }

    /**
     * @param stateOnly state matched, LGA did not -- the rows an admin can fix
     *                  wholesale by adding one alias
     * @param failed    key missing or provider error; still eligible for retry
     * @param remaining never-attempted rows left after this run
     */
    public record BackfillReport(
            int attempted,
            int resolved,
            int stateOnly,
            int unresolved,
            int failed,
            long remaining
    ) {
    }
}

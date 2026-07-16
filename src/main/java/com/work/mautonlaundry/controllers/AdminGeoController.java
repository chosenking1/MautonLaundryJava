package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.services.geo.AddressGeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin controls for canonical geography (Permission Architecture V2, spec §5).
 *
 * <p>Guarded by permission rather than role (spec §11: no hardcoded role names).
 * GEO_BACKFILL_RUN is deliberately separate from GEO_UNRESOLVED_VIEW: reading the
 * unresolved queue is free, while running a backfill spends Google quota per
 * address, so the two are delegable independently.
 */
@RestController
@RequestMapping("/api/v1/admin/geo")
@RequiredArgsConstructor
public class AdminGeoController {

    /** Bounded so one call cannot spend an unbounded amount of Google quota. */
    private static final int MAX_BACKFILL_BATCH = 500;

    private final AddressGeoService addressGeoService;
    private final AddressRepository addressRepository;

    /**
     * Resolves addresses that have never been attempted.
     *
     * <p>Manual rather than scheduled: every address is a billable Google call,
     * so this stays something a human starts and watches. Safe to re-run --
     * geo_resolved_at makes it idempotent.
     */
    @PostMapping("/backfill")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('GEO_BACKFILL_RUN')")
    public ResponseEntity<AddressGeoService.BackfillReport> backfill(
            @RequestParam(defaultValue = "50") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_BACKFILL_BATCH));
        return ResponseEntity.ok(addressGeoService.backfill(bounded));
    }

    /** How much work the backfill has left, without doing any of it. */
    @GetMapping("/pending")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('GEO_UNRESOLVED_VIEW')")
    public ResponseEntity<Map<String, Object>> pending() {
        return ResponseEntity.ok(Map.of(
                "neverAttempted", addressRepository.countUnresolvedWithCoordinates()
        ));
    }

    /**
     * The unresolved queue: addresses that were attempted but got no zone.
     *
     * <p>Almost always an LGA spelling we do not stock yet. Adding one
     * lga_aliases row fixes every future address in that LGA, so this list is
     * the input to a permanent fix rather than a per-row chore.
     */
    @GetMapping("/unresolved")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('GEO_UNRESOLVED_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> unresolved(
            @RequestParam(defaultValue = "50") int limit) {
        List<Address> rows = addressRepository.findResolvedWithoutLga(
                PageRequest.of(0, Math.max(1, Math.min(limit, MAX_BACKFILL_BATCH))));
        return ResponseEntity.ok(rows.stream().map(a -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("addressId", a.getId());
            m.put("street", a.getStreet());
            m.put("city", a.getCity());
            m.put("rawState", a.getState());
            m.put("stateId", a.getStateId());
            m.put("latitude", a.getLatitude());
            m.put("longitude", a.getLongitude());
            m.put("geoResolvedAt", a.getGeoResolvedAt());
            return m;
        }).toList());
    }

    /**
     * Re-runs resolution for one address, e.g. straight after adding the alias
     * that should now make it resolve.
     */
    @PostMapping("/{addressId}/resolve")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('GEO_BACKFILL_RUN')")
    public ResponseEntity<?> resolveOne(@PathVariable String addressId) {
        return ResponseEntity.ok(addressGeoService.resolveAndPersist(addressId));
    }
}

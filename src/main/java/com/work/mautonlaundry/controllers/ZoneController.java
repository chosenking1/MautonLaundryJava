package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.State;
import com.work.mautonlaundry.data.model.Zone;
import com.work.mautonlaundry.data.repository.LgaRepository;
import com.work.mautonlaundry.data.repository.StateRepository;
import com.work.mautonlaundry.data.repository.ZoneRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.geo.GeoNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zone management (Permission Architecture V2, §5 — the ZONE scope level).
 *
 * <p>A zone is an operational unit made of one or more LGAs within a state. An
 * LGA belongs to at most one zone; a zone may be a single LGA or several joined.
 * So a small state might run three zones, a dense one like Lagos twenty-one.
 *
 * <p>Exactly parallel to regions, one level down: a region groups states, a zone
 * groups LGAs. Reuses REGION_VIEW / REGION_MANAGE -- managing the geographic map
 * is one capability, and splitting "manage regions" from "manage zones" would
 * fragment it for no one's benefit.
 *
 * <p>An LGA lives in at most one zone (V24's unique index), so the LGA palette
 * reports each LGA's current zone and adding one that is already placed is
 * skipped -- a move is a deliberate remove-then-add.
 */
@RestController
@RequestMapping("/api/v1/admin/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final LgaRepository lgaRepository;
    private final StateRepository stateRepository;

    /** Zones of a state, each with its LGA ids. */
    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> byState(@RequestParam Integer stateId) {
        return ResponseEntity.ok(zoneRepository.findByStateIdOrderByName(stateId).stream()
                .map(z -> Map.<String, Object>of(
                        "id", z.getId(),
                        "name", z.getName(),
                        "lgaIds", zoneRepository.findLgaIds(z.getId())))
                .toList());
    }

    /**
     * Every LGA of a state with the zone it currently belongs to (null if none) --
     * the builder's palette. Reporting the current zone keeps a move deliberate.
     */
    @GetMapping("/lgas")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> lgas(@RequestParam Integer stateId) {
        return ResponseEntity.ok(lgaRepository.findByStateIdOrderByName(stateId).stream()
                .map(l -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", l.getId());
                    m.put("name", l.getName());
                    m.put("zoneId", zoneRepository.findZoneOfLga(l.getId()).orElse(null));
                    return m;
                })
                .toList());
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateZoneRequest request) {
        State state = stateRepository.findById(request.stateId).orElse(null);
        if (state == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unknown state."));
        }
        String normalized = GeoNormalizer.normalize(request.name);
        if (normalized.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A zone needs a name."));
        }
        if (zoneRepository.findByStateIdAndNormalizedName(request.stateId, normalized).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "That state already has a zone by that name."));
        }

        Zone zone = new Zone();
        zone.setId(UUID.randomUUID().toString());
        zone.setState(state);
        zone.setName(request.name.trim());
        zone.setNormalizedName(normalized);
        zone.setCreatedBy(SecurityUtil.getCurrentUserId());
        zone.setCreatedAt(LocalDateTime.now());
        zoneRepository.save(zone);
        return ResponseEntity.ok(Map.of("id", zone.getId(), "name", zone.getName()));
    }

    /** Adds an LGA to a zone. Skipped if the LGA is already in one (V24). */
    @PostMapping("/{zoneId}/lgas/{lgaId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    @Transactional
    public ResponseEntity<Void> addLga(@PathVariable String zoneId, @PathVariable Integer lgaId) {
        zoneRepository.addLga(zoneId, lgaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{zoneId}/lgas/{lgaId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    @Transactional
    public ResponseEntity<Void> removeLga(@PathVariable String zoneId, @PathVariable Integer lgaId) {
        zoneRepository.removeLga(zoneId, lgaId);
        return ResponseEntity.noContent().build();
    }

    public static class CreateZoneRequest {
        @NotNull public Integer stateId;
        @NotBlank public String name;
    }
}

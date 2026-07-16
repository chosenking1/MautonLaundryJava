package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Lga;
import com.work.mautonlaundry.data.model.State;
import com.work.mautonlaundry.data.repository.LgaRepository;
import com.work.mautonlaundry.data.repository.StateRepository;
import com.work.mautonlaundry.services.geo.GeoNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Zone (LGA) management within a state (Permission Architecture V2, §5).
 *
 * <p>ZONE is the LGA scope level. The 774 official LGAs are seeded in V14, but a
 * business may want operational zones the official register does not have -- a
 * large LGA split into delivery areas, say -- so this lets an admin add zones to
 * a state. A zone belongs to exactly one state (set at creation, via the
 * lgas.state_id column), so unlike a region there is nothing to assign
 * afterwards; creating it under the state is the whole operation.
 *
 * <p>Reuses REGION_VIEW / REGION_MANAGE rather than minting a fourth geo
 * permission. Regions, states and zones are one map that one role edits;
 * splitting "manage regions" from "manage zones" would fragment a single
 * capability without giving anyone a reason to hold one but not the other.
 *
 * <p>Resolver note: a created zone whose normalized name Google never returns
 * simply will not auto-resolve from an address's coordinates -- which is
 * harmless, since resolution failing leaves the zone unset rather than wrong. It
 * is still fully usable as a ZONE scope target and can be set on an address
 * manually or via an lga_aliases entry.
 */
@RestController
@RequestMapping("/api/v1/admin/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final LgaRepository lgaRepository;
    private final StateRepository stateRepository;

    /** The zones of a state. */
    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> byState(@RequestParam Integer stateId) {
        return ResponseEntity.ok(lgaRepository.findByStateIdOrderByName(stateId).stream()
                .map(l -> Map.<String, Object>of("id", l.getId(), "name", l.getName()))
                .toList());
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateZoneRequest request) {
        State state = stateRepository.findById(request.stateId).orElse(null);
        if (state == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unknown state."));
        }

        // Normalize with the same routine the resolver uses, so the uniqueness
        // check matches how a future Google-resolved name would be compared.
        String normalized = GeoNormalizer.normalizeLga(request.name);
        if (normalized.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A zone needs a name."));
        }
        if (lgaRepository.findByStateIdAndNormalizedName(request.stateId, normalized).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "That state already has a zone by that name."));
        }

        Lga lga = new Lga();
        lga.setState(state);
        lga.setName(request.name.trim());
        lga.setNormalizedName(normalized);
        lga.setCreatedAt(LocalDateTime.now());
        Lga saved = lgaRepository.save(lga);

        return ResponseEntity.ok(Map.of("id", saved.getId(), "name", saved.getName()));
    }

    public static class CreateZoneRequest {
        @NotNull public Integer stateId;
        @NotBlank public String name;
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Region;
import com.work.mautonlaundry.data.model.State;
import com.work.mautonlaundry.data.repository.RegionRepository;
import com.work.mautonlaundry.data.repository.StateRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Region Management (Permission Architecture V2, spec §7.7).
 *
 * <p>A region is a named group of states, and REGIONAL scope resolves through it,
 * so editing the map changes what regional staff can see. REGION_MANAGE gates the
 * writes; REGION_VIEW gates reads and the state list the builder offers.
 *
 * <p>A state belongs to at most one region (V13). Moving a state is therefore an
 * explicit remove-then-add, never a silent reassignment: {@code addState} skips
 * on conflict, and the state list reports each state's current region so the UI
 * can show it rather than surprise the operator.
 */
@RestController
@RequestMapping("/api/v1/admin/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;
    private final StateRepository stateRepository;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(regionRepository.findAll().stream()
                .sorted(Comparator.comparing(Region::getRegionName))
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "name", r.getRegionName(),
                        "stateIds", regionRepository.findStateIds(r.getId())))
                .toList());
    }

    /**
     * Every state with the region it currently belongs to (null if none) -- the
     * builder's palette. Reporting the current region is what keeps a move
     * deliberate rather than accidental.
     */
    @GetMapping("/states")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> states() {
        return ResponseEntity.ok(stateRepository.findAll().stream()
                .sorted(Comparator.comparing(State::getName))
                .map(s -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("regionId", regionRepository.findRegionOfState(s.getId()).orElse(null));
                    return m;
                })
                .toList());
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateRegionRequest request) {
        if (regionRepository.findByRegionName(request.name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A region with that name already exists."));
        }
        Region region = new Region();
        region.setId(UUID.randomUUID().toString());
        region.setRegionName(request.name);
        region.setCreatedBy(SecurityUtil.getCurrentUserId());
        region.setCreatedAt(LocalDateTime.now());
        regionRepository.save(region);
        return ResponseEntity.ok(Map.of("id", region.getId(), "name", region.getRegionName()));
    }

    @PostMapping("/{regionId}/states/{stateId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    @Transactional
    public ResponseEntity<Void> addState(@PathVariable String regionId, @PathVariable Integer stateId) {
        regionRepository.addState(regionId, stateId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{regionId}/states/{stateId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('REGION_MANAGE')")
    @Transactional
    public ResponseEntity<Void> removeState(@PathVariable String regionId, @PathVariable Integer stateId) {
        regionRepository.removeState(regionId, stateId);
        return ResponseEntity.noContent().build();
    }

    public static class CreateRegionRequest {
        @NotBlank public String name;
    }
}

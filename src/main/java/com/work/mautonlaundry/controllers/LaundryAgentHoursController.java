package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.LaundryAgentHours;
import com.work.mautonlaundry.services.LaundryAgentHoursService;
import com.work.mautonlaundry.services.LaundryAgentHoursService.DayWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for managing per-laundry-agent operating hours.
 *
 *  GET /api/v1/admin/laundry-agents/{agentId}/hours
 *      → [{ "dayOfWeek": 1, "openingTime": "08:00", "closingTime": "18:00" }, ...]
 *      Empty list = "always open" (no rows configured yet).
 *
 *  PUT /api/v1/admin/laundry-agents/{agentId}/hours
 *      Body: { "windows": [{ "dayOfWeek": 1, "openingTime": "08:00", "closingTime": "18:00" }, ...] }
 *      Bulk-replaces every window for that agent. Days not present in the
 *      payload are treated as closed for the day. Validation: dayOfWeek 1..7
 *      (Mon..Sun), unique days, closing > opening.
 */
@RestController
@RequestMapping("/api/v1/admin/laundry-agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LaundryAgentHoursController {

    private final LaundryAgentHoursService laundryAgentHoursService;

    @GetMapping("/{agentId}/hours")
    public ResponseEntity<List<Map<String, Object>>> getHours(@PathVariable String agentId) {
        List<LaundryAgentHours> rows = laundryAgentHoursService.getHoursFor(agentId);
        List<Map<String, Object>> out = rows.stream()
                .map(r -> Map.<String, Object>of(
                        "dayOfWeek", (int) r.getDayOfWeek(),
                        "openingTime", r.getOpeningTime().toString(),
                        "closingTime", r.getClosingTime().toString()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{agentId}/hours")
    public ResponseEntity<List<Map<String, Object>>> setHours(
            @PathVariable String agentId,
            @RequestBody UpdateHoursRequest body) {
        List<DayWindow> windows = body.windows == null
                ? List.of()
                : body.windows.stream()
                        .map(w -> new DayWindow(
                                w.dayOfWeek,
                                LocalTime.parse(w.openingTime),
                                LocalTime.parse(w.closingTime)))
                        .toList();
        List<LaundryAgentHours> saved = laundryAgentHoursService.replaceHours(agentId, windows);
        List<Map<String, Object>> out = saved.stream()
                .map(r -> Map.<String, Object>of(
                        "dayOfWeek", (int) r.getDayOfWeek(),
                        "openingTime", r.getOpeningTime().toString(),
                        "closingTime", r.getClosingTime().toString()))
                .toList();
        return ResponseEntity.ok(out);
    }

    public static class UpdateHoursRequest {
        public List<DayWindowDto> windows;
    }

    public static class DayWindowDto {
        public int dayOfWeek;
        public String openingTime;
        public String closingTime;
    }
}

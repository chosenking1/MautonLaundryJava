package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.analytics.LeaderboardEntry;
import com.work.mautonlaundry.services.CustomerIntelligenceService;
import com.work.mautonlaundry.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/leaderboards")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LeaderboardController {

    private final CustomerIntelligenceService customerIntelligenceService;

    @GetMapping
    public ResponseEntity<List<LeaderboardEntry>> leaderboard(
            @RequestParam(defaultValue = "LIFETIME_SPEND") String metric,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(customerIntelligenceService.leaderboard(metric, limit));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(defaultValue = "LIFETIME_SPEND") String metric,
            @RequestParam(defaultValue = "10") int limit) {
        List<LeaderboardEntry> rows = customerIntelligenceService.leaderboard(metric, limit);
        String csv = CsvUtil.toCsv(
                List.of("Rank", "Customer", metric),
                rows.stream().map(e -> List.of(
                        CsvUtil.s(e.rank()), CsvUtil.s(e.name()), CsvUtil.s(e.value()))).toList());
        return CsvUtil.download(csv, "leaderboard-" + metric.toLowerCase() + ".csv");
    }
}

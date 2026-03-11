package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.analytics.DashboardAnalyticsResponse;
import com.work.mautonlaundry.dtos.responses.analytics.MonthlyStatsResponse;
import com.work.mautonlaundry.dtos.responses.analytics.TimeSeriesResponse;
import com.work.mautonlaundry.dtos.responses.analytics.UsersByRoleResponse;
import com.work.mautonlaundry.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_READ')")
@RequiredArgsConstructor
public class AnalyticsController {
    
    private final AnalyticsService analyticsService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardAnalyticsResponse> getDashboardAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics(from, to));
    }
    
    @GetMapping("/users-by-role")
    public ResponseEntity<UsersByRoleResponse> getUsersByRole() {
        return ResponseEntity.ok(analyticsService.getUsersByRole());
    }
    
    @GetMapping("/monthly-stats")
    public ResponseEntity<MonthlyStatsResponse> getMonthlyStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(analyticsService.getMonthlyStats(year, month));
    }
    
    @GetMapping("/timeseries")
    public ResponseEntity<TimeSeriesResponse> getTimeSeries(
            @RequestParam String metric,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") String groupBy) {
        return ResponseEntity.ok(analyticsService.getTimeSeries(metric, from, to, groupBy));
    }
}

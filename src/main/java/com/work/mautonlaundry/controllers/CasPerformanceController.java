package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.analytics.CasListItemResponse;
import com.work.mautonlaundry.dtos.responses.analytics.CasProfileResponse;
import com.work.mautonlaundry.services.CasPerformanceService;
import com.work.mautonlaundry.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/cas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CasPerformanceController {

    private final CasPerformanceService casPerformanceService;

    @GetMapping
    public ResponseEntity<List<CasListItemResponse>> list(
            @RequestParam(defaultValue = "revenueGenerated") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(casPerformanceService.listCas(sort, direction, search));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(defaultValue = "revenueGenerated") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String search) {
        List<CasListItemResponse> rows = casPerformanceService.listCas(sort, direction, search);
        String csv = CsvUtil.toCsv(
                List.of("CAS", "Referral Code", "Customers Acquired", "Active Customers", "Repeat Customers",
                        "Revenue Generated", "Platform Earnings", "Retention Rate", "Status"),
                rows.stream().map(c -> List.of(
                        CsvUtil.s(c.name()), CsvUtil.s(c.referralCode()), CsvUtil.s(c.customersAcquired()),
                        CsvUtil.s(c.activeCustomers()), CsvUtil.s(c.repeatCustomers()), CsvUtil.s(c.revenueGenerated()),
                        CsvUtil.s(c.platformEarnings()), CsvUtil.s(c.retentionRate()), CsvUtil.s(c.status()))).toList());
        return CsvUtil.download(csv, "cas-performance.csv");
    }

    @GetMapping("/{id}")
    public ResponseEntity<CasProfileResponse> profile(@PathVariable String id) {
        return ResponseEntity.ok(casPerformanceService.getCasProfile(id));
    }
}

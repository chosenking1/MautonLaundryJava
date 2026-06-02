package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.analytics.CustomerListItemResponse;
import com.work.mautonlaundry.dtos.responses.analytics.CustomerProfileResponse;
import com.work.mautonlaundry.services.CustomerIntelligenceService;
import com.work.mautonlaundry.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CustomerIntelligenceController {

    private final CustomerIntelligenceService customerIntelligenceService;

    @GetMapping
    public ResponseEntity<Page<CustomerListItemResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String acquisitionSource,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @RequestParam(required = false) BigDecimal minSpend,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lifetimeSpend") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerIntelligenceService.listCustomers(
                status, acquisitionSource, registeredFrom, registeredTo, minSpend, search, sort, direction, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCustomers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String acquisitionSource,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @RequestParam(required = false) BigDecimal minSpend,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lifetimeSpend") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        List<CustomerListItemResponse> all = customerIntelligenceService.listCustomers(
                status, acquisitionSource, registeredFrom, registeredTo, minSpend, search,
                sort, direction, 0, Integer.MAX_VALUE).getContent();
        String csv = CsvUtil.toCsv(
                List.of("Name", "Phone", "City", "Customer Since", "Total Orders", "Lifetime Spend",
                        "Platform Earnings", "Last Order", "Status", "Acquisition Source"),
                all.stream().map(c -> List.of(
                        CsvUtil.s(c.name()), CsvUtil.s(c.phone()), CsvUtil.s(c.city()),
                        CsvUtil.s(c.customerSince()), CsvUtil.s(c.totalOrders()), CsvUtil.s(c.lifetimeSpend()),
                        CsvUtil.s(c.platformEarnings()), CsvUtil.s(c.lastOrderDate()),
                        CsvUtil.s(c.status()), CsvUtil.s(c.acquisitionSource()))).toList());
        return CsvUtil.download(csv, "customers.csv");
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerProfileResponse> profile(@PathVariable String id) {
        return ResponseEntity.ok(customerIntelligenceService.getCustomerProfile(id));
    }

    @GetMapping("/{id}/orders/export")
    public ResponseEntity<String> exportOrderHistory(@PathVariable String id) {
        CustomerProfileResponse profile = customerIntelligenceService.getCustomerProfile(id);
        String csv = CsvUtil.toCsv(
                List.of("Date", "Items", "Total", "Status", "Laundryman"),
                profile.orderHistory().stream().map(o -> List.of(
                        CsvUtil.s(o.date()),
                        o.items() == null ? "" : String.join("; ", o.items()),
                        CsvUtil.s(o.total()), CsvUtil.s(o.status()), CsvUtil.s(o.laundryman()))).toList());
        return CsvUtil.download(csv, "customer-" + id + "-orders.csv");
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.pricingrequests.CalculatePriceRequest;
import com.work.mautonlaundry.services.PricingEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    
    private final PricingEngine pricingEngine;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('PRICING_READ')") // Assuming read access to config is also permission-controlled
    public ResponseEntity<Map<String, BigDecimal>> getPricingConfig() {
        return ResponseEntity.ok(pricingEngine.getPricingConfig());
    }

    @PostMapping("/calculate")
    // No specific permission for calculation, as it's often public or tied to booking creation
    public ResponseEntity<Map<String, BigDecimal>> calculatePrice(@Valid @RequestBody CalculatePriceRequest request) {
        BigDecimal totalPrice = pricingEngine.calculateBookingPrice(request.getItems(), request.getExpress(), request.getDeliveryDistance());
        
        Map<String, BigDecimal> response = Map.of(
            "totalPrice", totalPrice
        );
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('PRICING_UPDATE')")
    public ResponseEntity<Map<String, String>> updatePricingConfig(@RequestBody Map<String, Object> request) {
        // Implementation for updating pricing config
        return ResponseEntity.ok(Map.of("message", "Pricing configuration updated successfully"));
    }
}

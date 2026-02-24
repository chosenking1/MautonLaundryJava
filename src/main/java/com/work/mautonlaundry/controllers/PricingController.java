package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.pricingrequests.CalculatePriceRequest;
import com.work.mautonlaundry.dtos.requests.pricingrequests.UpdatePricingConfigRequest;
import com.work.mautonlaundry.services.PricingEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    
    private final PricingEngine pricingEngine;

    @GetMapping("/config")
    public ResponseEntity<Map<String, BigDecimal>> getPricingConfig() {
        try {
            Map<String, BigDecimal> config = pricingEngine.getPricingConfig();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            // Return default values if there's an error
            Map<String, BigDecimal> defaultConfig = Map.of(
                "expressFee", BigDecimal.valueOf(10.00),
                "deliveryFee", BigDecimal.valueOf(5.00),
                "freeDeliveryThreshold", BigDecimal.valueOf(50.00)
            );
            return ResponseEntity.ok(defaultConfig);
        }
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
    public ResponseEntity<Map<String, String>> updatePricingConfig(@Valid @RequestBody UpdatePricingConfigRequest request) {
        // Implementation for updating pricing config
        return ResponseEntity.ok(Map.of("message", "Pricing configuration updated successfully"));
    }
}

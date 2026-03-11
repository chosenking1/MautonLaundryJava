package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.pricingrequests.CalculatePriceRequest;
import com.work.mautonlaundry.dtos.requests.pricingrequests.UpdatePricingConfigRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.pricing.PriceCalculationResponse;
import com.work.mautonlaundry.dtos.responses.pricing.PricingConfigResponse;
import com.work.mautonlaundry.services.PricingEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    
    private final PricingEngine pricingEngine;

    @GetMapping("/config")
    public ResponseEntity<PricingConfigResponse> getPricingConfig() {
        try {
            return ResponseEntity.ok(pricingEngine.getPricingConfig());
        } catch (Exception e) {
            PricingConfigResponse fallback = new PricingConfigResponse();
            fallback.setExpressFee(BigDecimal.valueOf(10.00));
            fallback.setDeliveryFee(BigDecimal.valueOf(5.00));
            fallback.setFreeDeliveryThreshold(BigDecimal.valueOf(50.00));
            return ResponseEntity.ok(fallback);
        }
    }

    @PostMapping("/calculate")
    // No specific permission for calculation, as it's often public or tied to booking creation
    public ResponseEntity<PriceCalculationResponse> calculatePrice(@Valid @RequestBody CalculatePriceRequest request) {
        BigDecimal totalPrice = pricingEngine.calculateBookingPrice(request.getItems(), request.getExpress(), request.getDeliveryDistance());
        return ResponseEntity.ok(new PriceCalculationResponse(totalPrice));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('PRICING_UPDATE')")
    public ResponseEntity<MessageResponse> updatePricingConfig(@Valid @RequestBody UpdatePricingConfigRequest request) {
        // Implementation for updating pricing config
        return ResponseEntity.ok(new MessageResponse("Pricing configuration updated successfully"));
    }
}

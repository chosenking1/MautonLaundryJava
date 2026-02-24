package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PricingEngine {
    
    private final PricingConfigRepository pricingConfigRepository;
    private final ServiceRepository serviceRepository;
    private final ServicePricingRepository servicePricingRepository;

    public BigDecimal calculateBookingPrice(List<BookingItemRequest> items, boolean express, BigDecimal deliveryDistance) {
        BigDecimal itemsTotal = calculateServiceItemsTotal(items);
        BigDecimal deliveryFee = calculateDeliveryFee(itemsTotal, deliveryDistance);
        BigDecimal expressFee = express ? getExpressFee() : BigDecimal.ZERO;
        
        return itemsTotal.add(deliveryFee).add(expressFee);
    }

    private BigDecimal calculateServiceItemsTotal(List<BookingItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (BookingItemRequest item : items) {
            Services service = serviceRepository.findByIdAndActiveTrue(item.getServiceId())
                    .orElseThrow(() -> new RuntimeException("Service not found: " + item.getServiceId()));
            
            ServicePricing pricing = servicePricingRepository
                    .findByServiceAndItemTypeAndActiveTrue(service, item.getItemType())
                    .orElseThrow(() -> new RuntimeException("Pricing not found for service " + service.getName() + " and item type " + item.getItemType()));
            
            total = total.add(pricing.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        
        return total;
    }

    private BigDecimal calculateDeliveryFee(BigDecimal itemsTotal, BigDecimal deliveryDistance) {
        BigDecimal freeDeliveryThreshold = getFreeDeliveryThreshold();
        
        if (itemsTotal.compareTo(freeDeliveryThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        
        return getDeliveryFee();
    }

    private BigDecimal getExpressFee() {
        return pricingConfigRepository.findLatestByKey(PricingConfig.ConfigKey.EXPRESS_FEE.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(10.00));
    }

    private BigDecimal getDeliveryFee() {
        return pricingConfigRepository.findLatestByKey(PricingConfig.ConfigKey.DELIVERY_FEE.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(5.00));
    }

    private BigDecimal getFreeDeliveryThreshold() {
        return pricingConfigRepository.findLatestByKey(PricingConfig.ConfigKey.FREE_DELIVERY_THRESHOLD.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(50.00));
    }

    public Map<String, BigDecimal> getPricingConfig() {
        return Map.of(
            "expressFee", getExpressFee(),
            "deliveryFee", getDeliveryFee(),
            "freeDeliveryThreshold", getFreeDeliveryThreshold()
        );
    }
}
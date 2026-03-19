package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.pricingrequests.UpdatePricingConfigRequest;
import com.work.mautonlaundry.dtos.responses.pricing.PricingConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    public BigDecimal calculateDeliveryFee(BigDecimal itemsTotal) {
        return calculateDeliveryFee(itemsTotal, BigDecimal.ZERO);
    }

    public BigDecimal calculateExpressFee(boolean express) {
        return express ? getExpressFee() : BigDecimal.ZERO;
    }

    private BigDecimal getExpressFee() {
        return pricingConfigRepository.findTopByKeyOrderByEffectiveFromDesc(PricingConfig.ConfigKey.EXPRESS_FEE.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(10.00));
    }

    private BigDecimal getDeliveryFee() {
        return pricingConfigRepository.findTopByKeyOrderByEffectiveFromDesc(PricingConfig.ConfigKey.DELIVERY_FEE.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(5.00));
    }

    private BigDecimal getFreeDeliveryThreshold() {
        return pricingConfigRepository.findTopByKeyOrderByEffectiveFromDesc(PricingConfig.ConfigKey.FREE_DELIVERY_THRESHOLD.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(BigDecimal.valueOf(50.00));
    }

    public PricingConfigResponse getPricingConfig() {
        PricingConfigResponse response = new PricingConfigResponse();
        response.setExpressFee(getExpressFee());
        response.setDeliveryFee(getDeliveryFee());
        response.setFreeDeliveryThreshold(getFreeDeliveryThreshold());
        return response;
    }

    @Transactional
    public PricingConfigResponse updatePricingConfig(UpdatePricingConfigRequest request) {
        boolean updated = false;
        if (request.getExpressFee() != null) {
            upsertConfig(PricingConfig.ConfigKey.EXPRESS_FEE, request.getExpressFee());
            updated = true;
        }
        if (request.getDeliveryFee() != null) {
            upsertConfig(PricingConfig.ConfigKey.DELIVERY_FEE, request.getDeliveryFee());
            updated = true;
        }
        if (request.getFreeDeliveryThreshold() != null) {
            upsertConfig(PricingConfig.ConfigKey.FREE_DELIVERY_THRESHOLD, request.getFreeDeliveryThreshold());
            updated = true;
        }
        if (!updated) {
            throw new IllegalArgumentException("No pricing values provided");
        }
        return getPricingConfig();
    }

    private void upsertConfig(PricingConfig.ConfigKey key, BigDecimal value) {
        PricingConfig config = pricingConfigRepository.findByKey(key.getValue())
                .orElseGet(PricingConfig::new);
        config.setKey(key.getValue());
        config.setValue(value.toPlainString());
        config.setEffectiveFrom(java.time.LocalDateTime.now());
        pricingConfigRepository.save(config);
    }
}

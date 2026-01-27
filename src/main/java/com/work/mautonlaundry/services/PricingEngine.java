package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.BookingLaundryItem;
import com.work.mautonlaundry.data.model.LaundryItemCatalog;
import com.work.mautonlaundry.data.model.PricingConfig;
import com.work.mautonlaundry.data.repository.LaundryItemCatalogRepository;
import com.work.mautonlaundry.data.repository.PricingConfigRepository;
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
    private final LaundryItemCatalogRepository catalogRepository;

    public BigDecimal calculateBookingPrice(List<BookingItemRequest> items, boolean express, BigDecimal deliveryDistance) {
        BigDecimal itemsTotal = calculateItemsTotal(items);
        BigDecimal deliveryFee = calculateDeliveryFee(itemsTotal, deliveryDistance);
        BigDecimal expressFee = express ? getExpressFee() : BigDecimal.ZERO;
        
        return itemsTotal.add(deliveryFee).add(expressFee);
    }

    private BigDecimal calculateItemsTotal(List<BookingItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (BookingItemRequest item : items) {
            LaundryItemCatalog catalogItem = catalogRepository.findByIdAndIsActiveTrue(item.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + item.getItemId()));
            
            BigDecimal unitPrice = "WHITE".equals(item.getColorType()) ? 
                    catalogItem.getBasePriceWhite() : catalogItem.getBasePriceColored();
            
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
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
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

        if (freeDeliveryThreshold != null
                && freeDeliveryThreshold.compareTo(BigDecimal.ZERO) > 0
                && itemsTotal.compareTo(freeDeliveryThreshold) >= 0) {
            return BigDecimal.ZERO;
        }

        return getDeliveryFee();
    }

    public BigDecimal calculateDeliveryFee(BigDecimal itemsTotal) {
        return calculateDeliveryFee(itemsTotal, BigDecimal.ZERO);
    }

    public boolean isFreeDeliveryTierMet(BigDecimal itemsTotal) {
        BigDecimal threshold = getFreeDeliveryThreshold();
        return threshold != null
                && threshold.compareTo(BigDecimal.ZERO) > 0
                && itemsTotal != null
                && itemsTotal.compareTo(threshold) >= 0;
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
                .orElse(BigDecimal.ZERO);
    }

    public PricingConfigResponse getPricingConfig() {
        PricingConfigResponse response = new PricingConfigResponse();
        response.setExpressFee(getExpressFee());
        response.setDeliveryFee(getDeliveryFee());
        response.setFreeDeliveryThreshold(getFreeDeliveryThreshold());
        response.setImototoCommissionRate(getImototoCommissionRate());
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
        if (request.getImototoCommissionRate() != null) {
            BigDecimal rate = request.getImototoCommissionRate();
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("imototoCommissionRate must be a fraction between 0 and 1 (e.g. 0.30 for 30%)");
            }
            upsertConfig(PricingConfig.ConfigKey.IMOTOTO_COMMISSION_RATE, rate);
            updated = true;
        }
        if (!updated) {
            throw new IllegalArgumentException("No pricing values provided");
        }
        return getPricingConfig();
    }

    /**
     * Imototo's commission rate as a decimal fraction (default 0.30). This is
     * the share of laundry/service revenue Imototo keeps and out of which
     * referral commission is paid. Admin-editable via pricing config.
     */
    public BigDecimal getImototoCommissionRate() {
        return pricingConfigRepository.findTopByKeyOrderByEffectiveFromDesc(PricingConfig.ConfigKey.IMOTOTO_COMMISSION_RATE.getValue())
                .map(config -> new BigDecimal(config.getValue()))
                .orElse(new BigDecimal("0.30"));
    }

    /**
     * Imototo's commission on a single booking: the configured commission rate
     * applied to the service revenue (items subtotal + express surcharge, after
     * any welcome discount; delivery fee excluded). Derived from booking totals:
     * {@code totalPrice} already includes items + delivery + express, so
     * {@code totalPrice - deliveryFee} is exactly items + express. The welcome
     * discount is then subtracted (Imototo earns on its net take). Never negative.
     */
    public BigDecimal calculateImototoCommissionForBooking(BigDecimal totalPrice,
                                                           BigDecimal deliveryFee,
                                                           BigDecimal discountAmount) {
        BigDecimal total = totalPrice == null ? BigDecimal.ZERO : totalPrice;
        BigDecimal delivery = deliveryFee == null ? BigDecimal.ZERO : deliveryFee;
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;

        BigDecimal commissionBase = total.subtract(delivery).subtract(discount);
        if (commissionBase.compareTo(BigDecimal.ZERO) < 0) {
            commissionBase = BigDecimal.ZERO;
        }
        return commissionBase.multiply(getImototoCommissionRate()).setScale(2, RoundingMode.HALF_UP);
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

package com.work.mautonlaundry.dtos.responses.pricing;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingConfigResponse {
    private BigDecimal expressFee;
    private BigDecimal deliveryFee;
    private BigDecimal freeDeliveryThreshold;
}

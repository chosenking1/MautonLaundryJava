package com.work.mautonlaundry.dtos.requests.pricingrequests;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePricingConfigRequest {
    private BigDecimal deliveryFee;
    private BigDecimal expressFee;
    private BigDecimal minimumOrderAmount;
}
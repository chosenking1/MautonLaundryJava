package com.work.mautonlaundry.dtos.requests.pricingrequests;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

@Data
public class UpdatePricingConfigRequest {
    private BigDecimal deliveryFee;
    private BigDecimal expressFee;
    @JsonAlias({"minimumOrderAmount"})
    private BigDecimal freeDeliveryThreshold;
    /** Imototo's commission as a decimal fraction (e.g. 0.30 = 30%). Drives platform earnings + referral commission. */
    private BigDecimal imototoCommissionRate;
}

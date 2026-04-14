package com.work.mautonlaundry.dtos.responses.discount;

import com.work.mautonlaundry.data.model.enums.DiscountCheckResult;
import com.work.mautonlaundry.data.model.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountEligibilityResponse {
    private DiscountCheckResult result;
    private boolean eligible;
    private String message;
    private String discountCode;
    private String discountName;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private Integer usagesRemaining;
    private Integer maxUsesPerUser;

    public static DiscountEligibilityResponse eligible(String code, String name, DiscountType type, 
            BigDecimal value, BigDecimal amount, Integer remaining, Integer maxPerUser) {
        return DiscountEligibilityResponse.builder()
                .eligible(true)
                .result(DiscountCheckResult.VALID)
                .discountCode(code)
                .discountName(name)
                .discountType(type)
                .discountValue(value)
                .discountAmount(amount)
                .usagesRemaining(remaining)
                .maxUsesPerUser(maxPerUser)
                .message("Discount applied successfully")
                .build();
    }

    public static DiscountEligibilityResponse ineligible(DiscountCheckResult result, String message) {
        return DiscountEligibilityResponse.builder()
                .eligible(false)
                .result(result)
                .message(message)
                .build();
    }
}
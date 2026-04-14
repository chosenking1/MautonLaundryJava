package com.work.mautonlaundry.dtos.responses.discount;

import com.work.mautonlaundry.data.model.enums.DiscountCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountApplicationResult {
    private boolean applied;
    private DiscountCheckResult result;
    private String message;
    private BigDecimal discountAmount;
    private BigDecimal finalOrderValue;
    private Integer usageCountAfter;
    private Integer maxUsesPerUser;

    public static DiscountApplicationResult applied(BigDecimal discountAmount, BigDecimal finalOrderValue, 
            int usageCountAfter, int maxUsesPerUser) {
        return DiscountApplicationResult.builder()
                .applied(true)
                .result(DiscountCheckResult.VALID)
                .discountAmount(discountAmount)
                .finalOrderValue(finalOrderValue)
                .usageCountAfter(usageCountAfter)
                .maxUsesPerUser(maxUsesPerUser)
                .message("Discount applied successfully")
                .build();
    }

    public static DiscountApplicationResult rejected(DiscountCheckResult result, String message) {
        return DiscountApplicationResult.builder()
                .applied(false)
                .result(result)
                .message(message)
                .build();
    }
}
package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueByPaymentMethod {
    private String method;
    private BigDecimal total;
}

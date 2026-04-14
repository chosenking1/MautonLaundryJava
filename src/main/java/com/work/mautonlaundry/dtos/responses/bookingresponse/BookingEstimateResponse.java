package com.work.mautonlaundry.dtos.responses.bookingresponse;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BookingEstimateResponse {
    private BigDecimal itemsTotal;
    private BigDecimal deliveryFee;
    private BigDecimal expressFee;
    private BigDecimal total;
    private boolean freeDelivery;
}

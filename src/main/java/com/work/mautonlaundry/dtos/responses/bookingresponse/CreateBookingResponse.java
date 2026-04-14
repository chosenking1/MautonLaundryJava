package com.work.mautonlaundry.dtos.responses.bookingresponse;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateBookingResponse {
    
    private String id;
    private String trackingNumber;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String discountCode;
    private LocalDateTime returnDate;
    private String status;
}

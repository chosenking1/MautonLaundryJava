package com.work.mautonlaundry.dtos.responses.paymentresponses;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePaymentResponse {
    private Long paymentId;
    private String bookingId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String transactionId;
    private LocalDateTime paymentDate;
    private String message;
}

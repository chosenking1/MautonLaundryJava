package com.work.mautonlaundry.dtos.requests.paymentrequests;

import com.work.mautonlaundry.data.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    private BigDecimal amount;


    private PaymentMethod paymentMethod;

    private String transactionId; // Optional, depending on method
}

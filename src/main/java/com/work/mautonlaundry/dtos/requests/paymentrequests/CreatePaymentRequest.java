package com.work.mautonlaundry.dtos.requests.paymentrequests;

import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private String bookingId;

    @NotNull
    @Positive
    private BigDecimal amount;


    @NotNull
    private PaymentMethod paymentMethod;

    private String transactionId; // Optional, depending on method
}

package com.work.mautonlaundry.dtos.requests.paymentrequests;

import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private String bookingId;

    @NotNull
    private BigDecimal amount;


    private PaymentMethod paymentMethod;

    private String transactionId; // Optional, depending on method
}

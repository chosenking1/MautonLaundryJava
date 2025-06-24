package com.work.mautonlaundry.dtos.requests.paymentrequests;

import com.work.mautonlaundry.data.model.PaymentStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentUpdateRequest {
    @Positive
    private BigDecimal amount;

    private PaymentStatus status;

    @Size(max = 255)
    private String transactionId;
}

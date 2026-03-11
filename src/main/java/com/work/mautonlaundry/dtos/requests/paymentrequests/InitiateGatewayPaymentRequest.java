package com.work.mautonlaundry.dtos.requests.paymentrequests;

import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import com.work.mautonlaundry.data.model.enums.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitiateGatewayPaymentRequest {
    @NotBlank(message = "Booking ID is required")
    private String bookingId;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than zero")
    private BigDecimal amount;
}

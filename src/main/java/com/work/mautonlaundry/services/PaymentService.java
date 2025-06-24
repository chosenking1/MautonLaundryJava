package com.work.mautonlaundry.services;

import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import jakarta.validation.Valid;

public interface PaymentService {
    void processPayment();

    void createPayment(CreatePaymentRequest request);

    void updatePayment();
    void deletePayment();
}

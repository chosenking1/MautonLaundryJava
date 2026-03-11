package com.work.mautonlaundry.services;

import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.dtos.requests.paymentrequests.PaymentUpdateRequest;
import com.work.mautonlaundry.dtos.responses.paymentresponses.CreatePaymentResponse;

import java.util.List;

public interface PaymentService {
    CreatePaymentResponse createPayment(CreatePaymentRequest request);

    CreatePaymentResponse getPaymentById(Long paymentId);

    CreatePaymentResponse getPaymentByBookingId(String bookingId);

    List<CreatePaymentResponse> getMyPayments();

    CreatePaymentResponse updatePaymentStatus(Long paymentId, PaymentUpdateRequest request);
}

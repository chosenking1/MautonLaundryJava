package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository, BookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void processPayment() {
        // Placeholder: Integration with external payment gateway goes here
        System.out.println("Processing payment with gateway...");
    }

    @Override
    @Transactional
    public void createPayment(CreatePaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionId(request.getTransactionId());
        payment.setStatus(PaymentStatus.PENDING); // Default status; update upon confirmation

        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void updatePayment() {
        // Placeholder: You can fetch and update a specific payment here
        // e.g., update status to SUCCESS or FAILED based on webhook or polling
        System.out.println("Updating payment status...");
    }

    @Override
    @Transactional
    public void deletePayment() {
        // This is only safe if payment is in a deletable state
        System.out.println("Deleting payment (not recommended for real systems unless it's soft-delete)...");
    }
}

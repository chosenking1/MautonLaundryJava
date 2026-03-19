package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.dtos.requests.paymentrequests.PaymentUpdateRequest;
import com.work.mautonlaundry.dtos.responses.paymentresponses.CreatePaymentResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.paymentexception.PaymentNotFoundException;
import com.work.mautonlaundry.exceptions.UnauthorizedException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        if (!currentUser.hasRole("ADMIN") && !booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Access denied");
        }

        paymentRepository.findByBooking_Id(booking.getId()).ifPresent(existing -> {
            throw new ConflictException("Payment already exists for this booking");
        });

        BigDecimal amount = booking.getTotalPrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = request.getAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionId(request.getTransactionId());
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved, "Payment created successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public CreatePaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));
        validateCanReadPayment(payment);
        return toResponse(payment, "Payment retrieved successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public CreatePaymentResponse getPaymentByBookingId(String bookingId) {
        Payment payment = paymentRepository.findByBooking_Id(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for booking"));
        validateCanReadPayment(payment);
        return toResponse(payment, "Payment retrieved successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatePaymentResponse> getMyPayments() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        return paymentRepository.findAll().stream()
                .filter(payment -> currentUser.hasRole("ADMIN")
                        || payment.getBooking().getUser().getId().equals(currentUser.getId()))
                .map(payment -> toResponse(payment, "Payment retrieved successfully"))
                .toList();
    }

    @Override
    @Transactional
    public CreatePaymentResponse updatePaymentStatus(Long paymentId, PaymentUpdateRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        if (!currentUser.hasRole("ADMIN")) {
            throw new ForbiddenOperationException("Only admin can update payment status");
        }

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero");
            }
            payment.setAmount(request.getAmount());
        }
        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            payment.setTransactionId(request.getTransactionId());
        }
        if (request.getStatus() != null) {
            payment.setStatus(request.getStatus());
        }

        Payment updated = paymentRepository.save(payment);
        return toResponse(updated, "Payment updated successfully");
    }

    private void validateCanReadPayment(Payment payment) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        if (!currentUser.hasRole("ADMIN") && !payment.getBooking().getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Access denied");
        }
    }

    private CreatePaymentResponse toResponse(Payment payment, String message) {
        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBooking().getId());
        response.setAmount(payment.getAmount());
        response.setPaymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null);
        response.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        response.setTransactionId(payment.getTransactionId());
        response.setPaymentDate(payment.getPaymentDate());
        response.setMessage(message);
        return response;
    }
}

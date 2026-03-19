package com.work.mautonlaundry.services.payments;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PaymentRepository;
import com.work.mautonlaundry.dtos.requests.paymentrequests.InitiateGatewayPaymentRequest;
import com.work.mautonlaundry.dtos.responses.paymentresponses.GatewayPaymentInitiationResponse;
import com.work.mautonlaundry.dtos.responses.paymentresponses.GatewayPaymentStatusResponse;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.UnauthorizedException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.paymentexception.PaymentNotFoundException;
import com.work.mautonlaundry.payments.gateway.PaymentGatewayAdapter;
import com.work.mautonlaundry.payments.gateway.model.GatewayInitiationResult;
import com.work.mautonlaundry.payments.gateway.model.GatewayWebhookEvent;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentGatewayService {
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGatewayRouter gatewayRouter;

    @Transactional
    public GatewayPaymentInitiationResponse initiatePayment(InitiateGatewayPaymentRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        if (!currentUser.hasRole("ADMIN") && !booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Access denied");
        }

        PaymentGatewayAdapter adapter = gatewayRouter.resolve(request.getProvider());

        paymentRepository.findByBooking_Id(booking.getId()).ifPresent(existing -> {
            if (existing.getStatus() == PaymentStatus.COMPLETED) {
                throw new ConflictException("Payment already completed for this booking");
            }
        });

        BigDecimal amount = booking.getTotalPrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = request.getAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Payment payment = paymentRepository.findByBooking_Id(booking.getId()).orElseGet(Payment::new);
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setProvider(request.getProvider());
        payment.setStatus(PaymentStatus.PENDING);

        GatewayInitiationResult initiation = adapter.initiatePayment(payment);
        payment.setTransactionId(initiation.getGatewayReference());
        payment.setCheckoutUrl(initiation.getCheckoutUrl());
        payment.setGatewayPayload(initiation.getRawPayload());
        Payment saved = paymentRepository.save(payment);

        GatewayPaymentInitiationResponse response = new GatewayPaymentInitiationResponse();
        response.setPaymentId(saved.getId());
        response.setBookingId(saved.getBooking().getId());
        response.setProvider(saved.getProvider().name());
        response.setPaymentStatus(saved.getStatus().name());
        response.setGatewayReference(saved.getTransactionId());
        response.setAccessCode(initiation.getAccessCode());
        response.setCheckoutUrl(saved.getCheckoutUrl());
        response.setMessage("Gateway payment initiated");
        return response;
    }

    @Transactional
    public void processWebhook(String providerRaw, Map<String, String> headers, String payload) {
        PaymentGatewayAdapter adapter = gatewayRouter.resolve(parseProvider(providerRaw));
        GatewayWebhookEvent event = adapter.verifyAndParseWebhook(headers, payload);

        if (event.getGatewayReference() == null || event.getGatewayReference().isBlank()) {
            throw new IllegalArgumentException("Missing gateway reference in webhook payload");
        }

        Payment payment = paymentRepository.findByTransactionId(event.getGatewayReference())
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for reference"));

        if (event.getEventId() != null && event.getEventId().equals(payment.getLastWebhookEventId())) {
            return;
        }

        payment.setStatus(event.getStatus());
        payment.setLastWebhookEventId(event.getEventId());
        payment.setGatewayPayload(event.getRawPayload());
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public GatewayPaymentStatusResponse getPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        if (!currentUser.hasRole("ADMIN") && !payment.getBooking().getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Access denied");
        }

        GatewayPaymentStatusResponse response = new GatewayPaymentStatusResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBooking().getId());
        response.setProvider(payment.getProvider() == null ? null : payment.getProvider().name());
        response.setPaymentStatus(payment.getStatus().name());
        response.setGatewayReference(payment.getTransactionId());
        response.setMessage("Payment status retrieved");
        return response;
    }

    private com.work.mautonlaundry.data.model.enums.PaymentProvider parseProvider(String providerRaw) {
        try {
            return com.work.mautonlaundry.data.model.enums.PaymentProvider.valueOf(providerRaw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid payment provider: " + providerRaw);
        }
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.paymentrequests.CreatePaymentRequest;
import com.work.mautonlaundry.dtos.requests.paymentrequests.InitiateGatewayPaymentRequest;
import com.work.mautonlaundry.dtos.responses.paymentresponses.CreatePaymentResponse;
import com.work.mautonlaundry.dtos.responses.paymentresponses.GatewayCallbackResponse;
import com.work.mautonlaundry.dtos.responses.paymentresponses.GatewayPaymentInitiationResponse;
import com.work.mautonlaundry.dtos.responses.paymentresponses.GatewayPaymentStatusResponse;
import com.work.mautonlaundry.dtos.responses.paymentresponses.WebhookAckResponse;
import com.work.mautonlaundry.services.PaymentService;
import com.work.mautonlaundry.services.payments.PaymentGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentGatewayService paymentGatewayService;

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<CreatePaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentResponse response = paymentService.createPayment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<CreatePaymentResponse> getPaymentById(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<CreatePaymentResponse> getPaymentByBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentByBookingId(bookingId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<List<CreatePaymentResponse>> getMyPayments() {
        return ResponseEntity.ok(paymentService.getMyPayments());
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<GatewayPaymentInitiationResponse> initiateGatewayPayment(
            @Valid @RequestBody InitiateGatewayPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentGatewayService.initiatePayment(request));
    }

    @PostMapping("/webhooks/{provider}")
    public ResponseEntity<WebhookAckResponse> handlePaymentWebhook(
            @PathVariable String provider,
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload) {
        paymentGatewayService.processWebhook(provider, headers, payload);
        return ResponseEntity.ok(new WebhookAckResponse("Webhook processed"));
    }

    @GetMapping("/{paymentId}/status")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<GatewayPaymentStatusResponse> getGatewayPaymentStatus(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentGatewayService.getPaymentStatus(paymentId));
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<GatewayCallbackResponse> handleGatewayRedirect(
            @PathVariable String provider,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String trxref) {
        String resolvedReference = (reference == null || reference.isBlank()) ? trxref : reference;
        GatewayCallbackResponse response = new GatewayCallbackResponse();
        response.setMessage("Redirect received. Confirm payment using webhook/status endpoint.");
        response.setProvider(provider);
        response.setReference(resolvedReference == null ? "" : resolvedReference);
        return ResponseEntity.ok(response);
    }

}

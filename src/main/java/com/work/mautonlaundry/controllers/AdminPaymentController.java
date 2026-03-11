package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.paymentrequests.PaymentUpdateRequest;
import com.work.mautonlaundry.dtos.responses.paymentresponses.CreatePaymentResponse;
import com.work.mautonlaundry.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PatchMapping("/{paymentId}")
    public ResponseEntity<CreatePaymentResponse> updatePayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentUpdateRequest request) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(paymentId, request));
    }
}

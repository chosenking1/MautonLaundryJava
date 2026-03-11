package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.deliveryrequests.AcceptDeliveryJobRequest;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AcceptDeliveryJobResponse;
import com.work.mautonlaundry.services.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryDispatchController {
    private final DeliveryService deliveryService;

    @PostMapping("/accept")
    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    public ResponseEntity<AcceptDeliveryJobResponse> acceptDeliveryJob(
            @Valid @RequestBody AcceptDeliveryJobRequest request) {
        return ResponseEntity.ok(deliveryService.acceptDeliveryJob(request));
    }
}

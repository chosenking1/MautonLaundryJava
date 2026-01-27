package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.servicerequests.CreateServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;
import com.work.mautonlaundry.services.LaundryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {
    
    private final LaundryService laundryService;

    @PostMapping
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse response = laundryService.createService(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable Long id,
            @RequestBody UpdateServiceRequest request) {
        ServiceResponse response = laundryService.updateService(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    public ResponseEntity<List<ServiceResponse>> getAllServices() {
        List<ServiceResponse> services = laundryService.getAllServices();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        ServiceResponse service = laundryService.getServiceById(id);
        return ResponseEntity.ok(service);
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    public ResponseEntity<List<ServiceResponse>> getServicesByCategory(@PathVariable String category) {
        List<ServiceResponse> services = laundryService.getServicesByCategory(category);
        return ResponseEntity.ok(services);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_DELETE')")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        laundryService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
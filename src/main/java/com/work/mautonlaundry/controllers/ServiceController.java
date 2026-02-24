package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;
import com.work.mautonlaundry.services.LaundryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {
    
    private final LaundryService laundryService;

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAllServices() {
        List<ServiceResponse> services = laundryService.getAllServices();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        ServiceResponse service = laundryService.getServiceById(id);
        return ResponseEntity.ok(service);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ServiceResponse>> getServicesByCategory(@PathVariable Long categoryId) {
        List<ServiceResponse> services = laundryService.getServicesByCategory(categoryId);
        return ResponseEntity.ok(services);
    }
}
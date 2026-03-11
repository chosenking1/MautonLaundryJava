package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.ServicePricing;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.ServicePricingRepository;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.CreateServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.requests.pricingrequests.CreateServicePricingRequest;
import com.work.mautonlaundry.dtos.requests.pricingrequests.UpdateServicePricingRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;
import com.work.mautonlaundry.services.LaundryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminServiceController {
    
    private final LaundryService laundryService;
    private final ServicePricingRepository servicePricingRepository;
    private final ServiceRepository serviceRepository;
    
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse response = laundryService.createService(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable Long id,
            @RequestBody UpdateServiceRequest request) {
        ServiceResponse response = laundryService.updateService(id, request);
        return ResponseEntity.ok(response);
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        laundryService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{serviceId}/pricing")
    public ResponseEntity<ServicePricing> createServicePricing(
            @PathVariable Long serviceId,
            @Valid @RequestBody CreateServicePricingRequest request) {
        Services service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        
        ServicePricing pricing = new ServicePricing();
        pricing.setService(service);
        pricing.setPriceType(request.getPriceType());
        pricing.setItemType(request.getItemType());
        pricing.setPrice(request.getPrice());
        pricing.setUnit(request.getDescription()); // Using unit field instead of description
        
        ServicePricing saved = servicePricingRepository.save(pricing);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/{serviceId}/pricing")
    public ResponseEntity<List<ServicePricing>> getServicePricing(@PathVariable Long serviceId) {
        List<ServicePricing> pricing = servicePricingRepository.findByServiceIdAndActiveTrue(serviceId);
        return ResponseEntity.ok(pricing);
    }

    @PutMapping("/{serviceId}/pricing/{pricingId}")
    public ResponseEntity<ServicePricing> updateServicePricing(
            @PathVariable Long serviceId,
            @PathVariable Long pricingId,
            @Valid @RequestBody UpdateServicePricingRequest request) {
        ServicePricing pricing = servicePricingRepository.findById(pricingId)
                .orElseThrow(() -> new RuntimeException("Pricing not found"));
        
        if (request.getPriceType() != null) {
            pricing.setPriceType(request.getPriceType());
        }
        if (request.getItemType() != null) {
            pricing.setItemType(request.getItemType());
        }
        if (request.getPrice() != null) {
            pricing.setPrice(request.getPrice());
        }
        if (request.getDescription() != null) {
            pricing.setUnit(request.getDescription());
        }
        
        ServicePricing updated = servicePricingRepository.save(pricing);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{serviceId}/pricing/{pricingId}")
    public ResponseEntity<Void> deleteServicePricing(
            @PathVariable Long serviceId,
            @PathVariable Long pricingId) {
        ServicePricing pricing = servicePricingRepository.findById(pricingId)
                .orElseThrow(() -> new RuntimeException("Pricing not found"));
        
        pricing.setActive(false);
        servicePricingRepository.save(pricing);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> getServiceImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                String contentType = "image/jpeg";
                if (filename.toLowerCase().endsWith(".png")) {
                    contentType = "image/png";
                } else if (filename.toLowerCase().endsWith(".gif")) {
                    contentType = "image/gif";
                }
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
}

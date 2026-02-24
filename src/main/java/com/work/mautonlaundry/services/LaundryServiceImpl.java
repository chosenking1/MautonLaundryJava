package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Category;
import com.work.mautonlaundry.data.model.ServicePricing;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.CategoryRepository;
import com.work.mautonlaundry.data.repository.ServicePricingRepository;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.CreateServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaundryServiceImpl implements LaundryService {
    
    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final ModelMapper mapper = new ModelMapper();
    
    @Autowired
    private AuditService auditService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public ServiceResponse createService(CreateServiceRequest request) {
        Services service = new Services();
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setImagePath(request.getImagePath());
        
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));
        
        if (!category.getActive()) {
            throw new RuntimeException("Cannot create service for inactive category");
        }
        service.setCategory(category);
        
        // Pricing logic removed as it's handled via ServicePricing entity now
        
        Services savedService = serviceRepository.save(service);
        auditService.logAction("CREATE", "SERVICE", savedService.getId().toString());
        
        return mapToResponse(savedService);
    }

    @Override
    public ServiceResponse updateService(Long id, UpdateServiceRequest request) {
        Services service = serviceRepository.findByIdAndDeletedFalseAndActiveTrue(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));

        if (request.getName() != null) {
            service.setName(request.getName());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription());
        }
        if (request.getImagePath() != null) {
            service.setImagePath(request.getImagePath());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            
            if (!category.getActive()) {
                throw new RuntimeException("Cannot update service to inactive category");
            }
            
            service.setCategory(category);
        }
        
        Services savedService = serviceRepository.save(service);
        auditService.logAction("UPDATE", "SERVICE", savedService.getId().toString());
        
        return mapToResponse(savedService);
    }

    @Override
    public List<ServiceResponse> getAllServices() {
        return serviceRepository.findByDeletedFalseAndActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse getServiceById(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalseAndActiveTrue(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        return mapToResponse(service);
    }

    @Override
    public List<ServiceResponse> getServicesByCategory(Long categoryId) {
        return serviceRepository.findByCategoryIdAndDeletedFalseAndActiveTrue(categoryId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteService(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalseAndActiveTrue(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        service.setDeleted(true);
        serviceRepository.save(service);
        auditService.logAction("DELETE", "SERVICE", id.toString());
    }

    private ServiceResponse mapToResponse(Services service) {
        ServiceResponse response = new ServiceResponse();
        response.setId(service.getId());
        response.setName(service.getName());
        response.setDescription(service.getDescription());
        if (service.getCategory() != null) {
            response.setCategoryName(service.getCategory().getName());
        }
        
        if (service.getImagePath() != null && !service.getImagePath().isEmpty()) {
            // Extract filename from path (remove "uploads/" prefix if present)
            String filename = service.getImagePath();
            if (filename.startsWith("uploads/")) {
                filename = filename.substring(8);
            }
            String imageUrl = "http://localhost:8079/api/v1/images/" + filename;
            response.setImagePath(imageUrl);
        }
        
        // Pricing mapping removed as it requires fetching ServicePricing list
        List<ServicePricing> pricing = servicePricingRepository.findByServiceIdAndActiveTrue(service.getId());
        response.setPricing(pricing);
        
        return response;
    }
}
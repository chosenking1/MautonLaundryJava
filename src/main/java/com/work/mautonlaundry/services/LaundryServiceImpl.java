package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ServicePrice;
import com.work.mautonlaundry.data.model.enums.ServiceType;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.CreateServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaundryServiceImpl implements LaundryService {
    
    private final ServiceRepository serviceRepository;
    private final ModelMapper mapper = new ModelMapper();
    
    @Autowired
    private AuditService auditService;

    @Override
    public ServiceResponse createService(CreateServiceRequest request) {
        if (request.getCategory() == null || request.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Service category cannot be null or empty");
        }
        
        Services service = new Services();
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setCategory(ServiceType.valueOf(request.getCategory().toUpperCase()));
        
        ServicePrice price = new ServicePrice();
        price.setPrice(request.getPrice());
        price.setWhite(request.getWhite());
        
        service.setServicePrice(price);
        
        Services savedService = serviceRepository.save(service);
        auditService.logAction("CREATE", "SERVICE", savedService.getId().toString());
        
        return mapToResponse(savedService);
    }

    @Override
    public ServiceResponse updateService(Long id, UpdateServiceRequest request) {
        Services service = serviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));

        if (request.getService_name() != null) {
            service.setName(request.getService_name());
        }
        if (request.getService_details() != null) {
            service.setDescription(request.getService_details());
        }
        if (request.getType_of_service() != null) {
            service.setCategory(request.getType_of_service());
        }
        
        ServicePrice price = service.getServicePrice();
        if (price == null) {
            price = new ServicePrice();
            service.setServicePrice(price);
        }
        
        if (request.getService_price() > 0) {
            price.setPrice((double) request.getService_price());
        }
        if (request.getService_price_white() > 0) {
            price.setWhite((double) request.getService_price_white());
        }
        
        Services savedService = serviceRepository.save(service);
        auditService.logAction("UPDATE", "SERVICE", savedService.getId().toString());
        
        return mapToResponse(savedService);
    }

    @Override
    public List<ServiceResponse> getAllServices() {
        return serviceRepository.findByDeletedFalse()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse getServiceById(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        return mapToResponse(service);
    }

    @Override
    public List<ServiceResponse> getServicesByCategory(String category) {
        ServiceType serviceType = ServiceType.valueOf(category.toUpperCase());
        return serviceRepository.findByCategoryAndDeletedFalse(serviceType)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteService(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalse(id)
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
        response.setCategory(service.getCategory().name());
        if (service.getServicePrice() != null) {
            response.setPrice(service.getServicePrice().getPrice());
            response.setWhite(service.getServicePrice().getWhite());
        }
        return response;
    }
}
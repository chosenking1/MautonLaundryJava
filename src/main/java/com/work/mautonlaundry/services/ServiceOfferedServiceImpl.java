package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ServicePrice;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.model.enums.ServiceType;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOfferedServiceImpl implements ServiceOfferedService {
    private final ServiceRepository serviceRepository;

    @Autowired
    private ModelMapper mapper;

    public ServiceOfferedServiceImpl(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AddServiceResponse addService(AddServiceRequest request) {
        Services service = new Services();
        service.setName(request.getService_name());
        service.setCategory(request.getType_of_service());
        service.setDescription(request.getService_details());

        ServicePrice price = new ServicePrice();
        price.setPrice(Double.valueOf(request.getService_price()));
        price.setWhite(Double.valueOf(request.getService_price_white()));
        
        service.setServicePrice(price);
        Services savedService = serviceRepository.save(service);

        AddServiceResponse response = new AddServiceResponse();
        response.setId(savedService.getId());
        response.setService_name(savedService.getName());
        response.setService_details(savedService.getDescription());
        response.setType_of_service(ServiceType.valueOf(savedService.getCategory().name()));
        response.setServicePrice(savedService.getServicePrice());

        return response;
    }

    @Override
    public ViewServiceResponse getServiceById(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        
        ViewServiceResponse response = new ViewServiceResponse();
        response.setId(service.getId());
        response.setService_name(service.getName());
        response.setService_details(service.getDescription());
        response.setType_of_service(ServiceType.valueOf(service.getCategory().name()));
        response.setServicePrice(service.getServicePrice());
        
        return response;
    }

    @Override
    public ServiceRepository getRepository() {
        return serviceRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UpdateServiceResponse serviceDetailsUpdate(UpdateServiceRequest request) {
        Services existingService = serviceRepository.findByIdAndDeletedFalse(request.getId())
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));

        existingService.setName(request.getService_name());
        existingService.setDescription(request.getService_details());
        existingService.setCategory(request.getType_of_service());
        
        ServicePrice price = existingService.getServicePrice();
        price.setPrice(Double.valueOf(request.getService_price()));
        price.setWhite(Double.valueOf(request.getService_price_white()));

        Services updatedService = serviceRepository.save(existingService);
        
        UpdateServiceResponse response = new UpdateServiceResponse();
        response.setId(updatedService.getId());
        response.setService_name(updatedService.getName());
        response.setService_details(updatedService.getDescription());
        response.setType_of_service(ServiceType.valueOf(updatedService.getCategory().name()));
        
        return response;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceById(Long id) {
        Services service = serviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        
        service.setDeleted(true);
        serviceRepository.save(service);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceByName(String serviceName) {
        // This method would need a repository method to find by name
        throw new UnsupportedOperationException("Delete by name not implemented");
    }
}

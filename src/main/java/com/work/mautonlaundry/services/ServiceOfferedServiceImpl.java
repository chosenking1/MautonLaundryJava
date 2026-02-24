package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Category;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.CategoryRepository;
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
    private final CategoryRepository categoryRepository;

    @Autowired
    private ModelMapper mapper;

    public ServiceOfferedServiceImpl(ServiceRepository serviceRepository, CategoryRepository categoryRepository) {
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AddServiceResponse addService(AddServiceRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        
        Services service = new Services();
        service.setName(request.getName());
        service.setCategory(category);
        service.setDescription(request.getDescription());
        service.setImagePath(request.getImagePath());

        Services savedService = serviceRepository.save(service);

        AddServiceResponse response = new AddServiceResponse();
        response.setId(savedService.getId());
        response.setName(savedService.getName());
        response.setDescription(savedService.getDescription());
        response.setCategoryName(savedService.getCategory().getName());
        response.setImagePath(savedService.getImagePath());

        return response;
    }

    @Override
    public ViewServiceResponse getServiceById(Long id) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        
        ViewServiceResponse response = new ViewServiceResponse();
        response.setId(service.getId());
        response.setName(service.getName());
        response.setDescription(service.getDescription());
        response.setCategoryName(service.getCategory().getName());
        response.setImagePath(service.getImagePath());
        
        return response;
    }

    @Override
    public ServiceRepository getRepository() {
        return serviceRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UpdateServiceResponse serviceDetailsUpdate(UpdateServiceRequest request) {
        Services existingService = serviceRepository.findById(request.getId())
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));

        if (request.getName() != null) {
            existingService.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existingService.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            existingService.setCategory(category);
        }
        if (request.getImagePath() != null) {
            existingService.setImagePath(request.getImagePath());
        }

        Services updatedService = serviceRepository.save(existingService);
        
        UpdateServiceResponse response = new UpdateServiceResponse();
        response.setId(updatedService.getId());
        response.setName(updatedService.getName());
        response.setDescription(updatedService.getDescription());
        response.setCategoryName(updatedService.getCategory().getName());
        response.setImagePath(updatedService.getImagePath());
        
        return response;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceById(Long id) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
        
        service.setDeleted(true);
        serviceRepository.save(service);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceByName(String serviceName) {
        throw new UnsupportedOperationException("Delete by name not implemented");
    }
}

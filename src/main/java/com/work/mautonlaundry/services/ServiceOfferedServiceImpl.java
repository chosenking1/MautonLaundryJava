package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.ServicePrice;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.repository.ServicePriceRepository;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceAlreadyExistException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.modelmapper.ModelMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOfferedServiceImpl implements ServiceOfferedService {
    private final ServiceRepository serviceRepository;


    private final ServicePriceRepository servicePriceRepository;

    @Autowired
    private ModelMapper mapper;

    public ServiceOfferedServiceImpl(ServiceRepository serviceRepository, ServicePriceRepository servicePriceRepository) {
        this.serviceRepository = serviceRepository;
        this.servicePriceRepository = servicePriceRepository;

    }


@PreAuthorize("hasRole('ADMIN')")
@Transactional
public AddServiceResponse addService(AddServiceRequest request) {
    if (serviceExist(request.getService_name())) {
        throw new ServiceAlreadyExistException("Service already exist");
    }

    Services service = new Services();
    service.setService_name(request.getService_name());
    service.setType_of_service(request.getType_of_service());
    service.setService_details(request.getService_details());
    service.setPhotos(request.getPhotos());

    Services savedService = serviceRepository.save(service);

    ServicePrice price = new ServicePrice();
    price.setPrice(request.getService_price());
    price.setWhite(request.getService_price_white());
    price.setService(savedService);

    ServicePrice savedPrice = servicePriceRepository.save(price);

    AddServiceResponse response = new AddServiceResponse();
    response.setId(savedService.getId());
    response.setService_name(savedService.getService_name());
    response.setService_details(savedService.getService_details());
    response.setType_of_service(savedService.getType_of_service());
    response.setPhotos(savedService.getPhotos());
    response.setServicePrice(savedPrice);

    return response;
}


    private boolean serviceExist(Long id) {
        return serviceRepository.existsById(id);
    }

    private boolean serviceExist(String service) {
        return serviceRepository.existsByServiceName(service);
    }

    private Services findServiceByServiceName(String serviceName) {
        return serviceRepository.findServicesByService_name(serviceName)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found"));
    }

    private Services findServiceById(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service doesn't exist"));
    }

    @Override
    public ViewServiceResponse getServiceById(Long id) {
        ViewServiceResponse response = new ViewServiceResponse();
        Services service = findServiceById(id);
        mapper.map(service, response);
        return response;
    }

    @Override
    public ServiceRepository getRepository() {
        return serviceRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UpdateServiceResponse serviceDetailsUpdate(UpdateServiceRequest request) {
        Services existingService = findServiceById(request.getId());
        ServicePrice existingPrice = servicePriceRepository.findServicePriceByService(existingService);

        existingPrice.setWhite(request.getService_price_white());
        existingPrice.setPrice(request.getService_price());
        existingPrice.setLocationMultiplier(existingPrice.getLocationMultiplier());

       ServicePrice updatedServicePrice = servicePriceRepository.save(existingPrice);

        existingService.setServicePrice(existingPrice);
        existingService.setService_details(request.getService_details());
        existingService.setPhotos(request.getPhotos());
        existingService.setService_name(request.getService_name());
        existingService.setType_of_service(request.getType_of_service());

        Services updatedService = serviceRepository.save(existingService);
        return mapper.map(updatedService, UpdateServiceResponse.class);
    }

    private void deleteService(Services service) {
            Services deletedService = serviceRepository.findServicesById(service.getId())
                    .orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));

            deletedService.setDeleted(true);
            serviceRepository.save(deletedService);

    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceByName(String serviceName) {
        if (!serviceExist(serviceName)) {
            throw new ServiceNotFoundException("Service not found");
        }
        Services service = serviceRepository.findServicesByService_name(serviceName).orElseThrow(()-> new ServiceNotFoundException("Service Doesnt Exist"));
        deleteService(service);
}

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public void deleteServiceById(Long id) {
        if (!serviceExist(id)) {
            throw new ServiceNotFoundException("Service not found");
        }
        Services service = serviceRepository.findServicesById(id).orElseThrow(()-> new ServiceNotFoundException("Service Doesnt Exist"));

        deleteService(service);}

}

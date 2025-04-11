package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceAlreadyExistException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import org.modelmapper.ModelMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ServiceOfferedServiceImpl implements ServiceOfferedService{
    @Autowired
    private final ServiceRepository serviceRepository ;

    @Autowired
    private  ModelMapper mapper;

    public ServiceOfferedServiceImpl(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public AddServiceResponse addService(AddServiceRequest request) {
        Services service= new Services();

        AddServiceResponse addResponse = new AddServiceResponse();

        if(serviceExist(request.getService_name())) {
            throw new ServiceAlreadyExistException("Service already exist");
        }
        else{
            service.setService_name(request.getService_name());
            service.setType_of_service(request.getType_of_service());
            service.setService_details(request.getService_details());
            service.setService_category(request.getService_category());
            service.setPhotos(request.getPhotos());
            service.setService_price(request.getService_price());
            service.setService_price_white(request.getService_price_white());
            Services serviceDetails = serviceRepository.save(service);
            mapper.map(serviceDetails, addResponse);}

        return addResponse;
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
        Optional<Services> services = Optional.ofNullable(serviceRepository.findById(id).orElseThrow(() -> new ServiceNotFoundException("Service Doesnt Exist")));
        mapper.map(services, response);
        return response;
    }

    @Override
    public ServiceRepository getRepository() {
        return serviceRepository;
    }



    @Override
    public UpdateServiceResponse serviceDetailsUpdate(UpdateServiceRequest request) {
        Services existingService = findServiceById(request.getId());
        mapper.map(request, existingService);
        Services updatedService = serviceRepository.save(existingService);
        return mapper.map(updatedService, UpdateServiceResponse.class);
    }


    @Override
    public void deleteService(Long id) {
        if (!serviceExist(id)) {
            throw new ServiceNotFoundException("Service not found");
        }
        serviceRepository.deleteById(id);
    }


}

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
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.modelmapper.ModelMapper;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ServiceOfferedServiceImpl implements ServiceOfferedService{
//    @Autowired
    private final ServiceRepository serviceRepository ;

    ModelMapper mapper = new ModelMapper();

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
            service.setPhotos(request.getPhotos());
            service.setService_details(request.getService_details());
            service.setPhotos(request.getPhotos());
            service.setService_price(request.getService_price());
            service.setService_price_white(request.getService_price_white());
            Services serviceDetails = serviceRepository.save(service);
            mapper.map(serviceDetails, addResponse);}

        return addResponse;
    }

    private boolean serviceExist(Long id) {
        return findServiceById(id) != null;
    }

    private boolean serviceExist(String service) {
        return findServiceByServiceName(service) != null;
    }

    private Services findServiceByServiceName(String service) {
        ViewServiceResponse response = new ViewServiceResponse();
//        Optional<Services> services = Optional.ofNullable(serviceRepository.findByService_name(service).orElseThrow(() -> new UserNotFoundException("Service Doesnt Exist")));
//        mapper.map(services, response);
//        return response;
        return serviceRepository.findServicesByService_name(service).orElseThrow(()-> new UsernameNotFoundException("user name not found"));

    }

    @Override
    public ViewServiceResponse findServiceById(Long id) {
        ViewServiceResponse response = new ViewServiceResponse();
        Optional<Services> services = Optional.ofNullable(serviceRepository.findById(id).orElseThrow(() -> new UserNotFoundException("Service Doesnt Exist")));
        mapper.map(services, response);
        return response;
    }
    @Override
    public ServiceRepository getRepository() {
        return serviceRepository;
    }



    @Override
    public UpdateServiceResponse serviceDetailsUpdate(UpdateServiceRequest request) {
        Services existingService = new Services();
        UpdateServiceResponse updateResponse = new UpdateServiceResponse();

        if(serviceExist(request.getId())) {
            mapper.map(request, existingService);
            serviceRepository.save(existingService);
            String message = "Details Updated Successfully";
            mapper.map(message, updateResponse);
            return updateResponse;
        }
        else{

            throw new ServiceNotFoundException("Service Not Found");

        }
    }

    @Override
    public void deleteService(Long id) {
        serviceRepository.deleteById(id);
    }

}

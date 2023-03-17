package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import org.springframework.stereotype.Service;

@Service
public class ServiceOfferedServiceImpl implements ServiceOfferedService{

    @Override
    public AddServiceResponse addService(AddServiceRequest request) {
        return null;
    }

    @Override
    public ServiceRepository getRepository() {
        return null;
    }

    @Override
    public ViewServiceResponse findServiceById(Long id) {
        return null;
    }

    @Override
    public UpdateServiceResponse userDetailsUpdate(UpdateServiceRequest request) {
        return null;
    }

    @Override
    public void deleteService(Long id) {

    }
}

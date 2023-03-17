package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.ServiceRepository;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;

public interface ServiceOfferedService {
    AddServiceResponse addService(AddServiceRequest request);

    ServiceRepository getRepository();

    ViewServiceResponse findServiceById(Long id);

    UpdateServiceResponse userDetailsUpdate(UpdateServiceRequest request);

    void deleteService(Long id);


}

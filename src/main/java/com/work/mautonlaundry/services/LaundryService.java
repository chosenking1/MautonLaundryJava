package com.work.mautonlaundry.services;

import com.work.mautonlaundry.dtos.requests.servicerequests.CreateServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ServiceResponse;

import java.util.List;

public interface LaundryService {
    ServiceResponse createService(CreateServiceRequest request);
    ServiceResponse updateService(Long id, UpdateServiceRequest request);
    List<ServiceResponse> getAllServices();
    ServiceResponse getServiceById(Long id);
    List<ServiceResponse> getServicesByCategory(String category);
    void deleteService(Long id);
}
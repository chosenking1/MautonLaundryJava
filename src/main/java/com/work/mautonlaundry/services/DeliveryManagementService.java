package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.DeliveryManagementRepository;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;

public interface DeliveryManagementService {
    PickupResponse createPickup(PickupRequest request);

    DeliveryManagementRepository getRepository();

    PickupStatusResponse findPickupById(Long id);
    PickupStatusResponse findPickupByEmail(String email);

    PickupStatusUpdateResponse pickupUpdate(PickupStatusUpdateRequest request);

    void deletePickup(Long id);
    void deletePickup(String Email);
}

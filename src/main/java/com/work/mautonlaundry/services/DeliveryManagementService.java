package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.DeliveryManagementRepository;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.DeliveryRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.CreateDeliveryResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;

public interface DeliveryManagementService {
    CreateDeliveryResponse createDeliveryDetails(DeliveryRequest request);

    DeliveryManagementRepository getRepository();

    PickupStatusResponse findPickupById(Long id);
    PickupStatusResponse findDeliveryByBookingId(Long bookingId);

    PickupStatusUpdateResponse pickupUpdate(PickupStatusUpdateRequest request);

    void deletePickup(Long id);
    void deletePickupByBookingId(Long bookingId);

    void setLaundryManAddress(Long id, Long bookingId);
}

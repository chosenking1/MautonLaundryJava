package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.DeliveryManagementRepository;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeliveryManagementServiceImpl implements DeliveryManagementService{
    @Autowired
    private DeliveryManagementRepository deliveryManagementRepository;

    ModelMapper mapper = new ModelMapper();
    /**
     * @param request
     * @return
     */
    @Override
    public PickupResponse createPickup(PickupRequest request) {

        return null;
    }

    /**
     * @return
     */
    @Override
    public DeliveryManagementRepository getRepository() {

        return null;
    }

    /**
     * @param id
     * @return
     */
    @Override
    public PickupStatusResponse findPickupById(Long id) {
        return null;
    }

    /**
     * @param email
     * @return
     */
    @Override
    public PickupStatusResponse findPickupByEmail(String email) {
        return null;
    }

    /**
     * @param request
     * @return
     */
    @Override
    public PickupStatusUpdateResponse pickupUpdate(PickupStatusUpdateRequest request) {
        return null;
    }

    /**
     * @param id
     */
    @Override
    public void deletePickup(Long id) {

    }

    /**
     * @param Email
     */
    @Override
    public void deletePickup(String Email) {

    }
}

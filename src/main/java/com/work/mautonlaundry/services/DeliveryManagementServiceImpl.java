package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.DeliveryManagement;
import com.work.mautonlaundry.data.model.UrgencyType;
import com.work.mautonlaundry.data.repository.DeliveryManagementRepository;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DeliveryManagementServiceImpl implements DeliveryManagementService{
    @Autowired
    private DeliveryManagementRepository deliveryRepository;
    ModelMapper mapper = new ModelMapper();
    /**
     * @param request
     * @return
     */
    @Override
    public PickupResponse createPickup(PickupRequest request) {
        DeliveryManagement deliveryManagement = new DeliveryManagement();
        PickupResponse pickupResponse = new PickupResponse();

        deliveryManagement.setAddress(request.getAddress());
        deliveryManagement.setEmail(request.getEmail());
        deliveryManagement.setBooking_id(request.getId());
        deliveryManagement.setPick_up(calculatePickUpDate(request.getDate_booked()));
        deliveryManagement.setUrgency(request.getUrgency());
        deliveryManagement.setReturn_date(calculateReturnDate(deliveryManagement.getPick_up(), request.getUrgency()));


        DeliveryManagement bookingDetails = deliveryRepository.save(deliveryManagement);

        mapper.map(bookingDetails, pickupResponse);
        return pickupResponse;
    }

    private LocalDateTime calculateReturnDate(LocalDateTime pick_up, UrgencyType urgency) {
        LocalDateTime returnDate;
        if (urgency == UrgencyType.NORMAL){
            returnDate = pick_up.plusDays(7);
        }
        else {
            returnDate = pick_up.plusDays(3);
        }
        return returnDate;
    }

    private LocalDateTime calculatePickUpDate(LocalDateTime date_booked) {
        return date_booked.plusDays(2);
    }

    /**
     * @return
     */
    @Override
    public DeliveryManagementRepository getRepository() {

        return deliveryRepository;
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

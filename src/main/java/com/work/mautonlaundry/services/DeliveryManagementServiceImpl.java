package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.DeliveryManagement;
import com.work.mautonlaundry.data.model.DeliveryStatus;
import com.work.mautonlaundry.data.model.UrgencyType;
import com.work.mautonlaundry.data.repository.DeliveryManagementRepository;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.ObjectUtils.isEmpty;

@Service
public class DeliveryManagementServiceImpl implements DeliveryManagementService{
    @Autowired
    private DeliveryManagementRepository deliveryRepository;
    ModelMapper mapper = new ModelMapper();

    /**
     * @param request a pickup
     * @return a string
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
        deliveryManagement.setDeliveryStatus(DeliveryStatus.PENDING_PICKUP);
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
        PickupStatusResponse response = new PickupStatusResponse();
        Optional<DeliveryManagement> deliveryManagement = Optional.ofNullable(deliveryRepository.findById(id).orElseThrow(() -> new BookingNotFoundException("Delivery Doesnt Exist")));
        mapper.map(deliveryManagement, response);
        return response;

    }

    /**
     * @param email
     * @return
     */
    @Override
    public PickupStatusResponse findPickupByEmail(String email) {
        PickupStatusResponse response = new PickupStatusResponse();
        Optional<DeliveryManagement> deliveryManagement = Optional.of(deliveryRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Delivery Doesnt Exist")));
        mapper.map(deliveryManagement, response);
        return response;

    }

    /**
     * @param request
     * @return
     */
    @Override
    public PickupStatusUpdateResponse pickupUpdate(PickupStatusUpdateRequest request) {
        DeliveryManagement existingDelivery = new DeliveryManagement();
        PickupStatusUpdateResponse updateResponse = new PickupStatusUpdateResponse();

        if(deliveryExist(request.getId())) {
            mapper.map(request, existingDelivery);
            deliveryRepository.save(existingDelivery);
            String message = "Details Updated Successfully";
            mapper.map(message, updateResponse);
            return updateResponse;
        }
        else{

            throw new ServiceNotFoundException("Delivery Not Found");

        }

    }

    private boolean deliveryExist(Long id) {
        return !isEmpty(findPickupById(id));
    }

    /**
     * @param id
     */
    @Override
    public void deletePickup(Long id) {
        deliveryRepository.deleteById(id);
    }

    /**
     * @param email
     */
    @Override
    public void deletePickup(String email) {
        deliveryRepository.deleteByEmail(email);
    }
}

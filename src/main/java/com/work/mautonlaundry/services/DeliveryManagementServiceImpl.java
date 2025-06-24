package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.DeliveryRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.CreateDeliveryResponse;
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

    @Autowired
    BookingRepository bookingRepository;

    BookingResourceRepository bookingResourceRepository;

    AddressRepository addressRepository;

    UserRepository userRepository;

    /**
     * @param request a pickup
     * @return a string
     */
    @Override
    public CreateDeliveryResponse createDeliveryDetails(DeliveryRequest request) {
        DeliveryManagement deliveryManagement = new DeliveryManagement();
        if (request == null) {
            throw new IllegalArgumentException("Delivery request must not be null.");
        }

        deliveryManagement.setUserAddress(request.getUserAddress());
//        deliveryManagement.setEmail(request.getEmail());
        deliveryManagement.setBooking_id(request.getBooking_id());
        deliveryManagement.setPick_up_date(calculatePickUpDate(request.getDate_booked()));
        deliveryManagement.setUserAddress(request.getUserAddress());
        deliveryManagement.setUrgency(request.getUrgency());
        deliveryManagement.setDeliveryStatus(DeliveryStatus.PENDING_PICKUP);
        deliveryManagement.setPick_up_date(calculatePickUpDate(request.getDate_booked()));
        deliveryManagement.setReturn_date(calculateReturnDate(deliveryManagement.getPick_up_date(), request.getUrgency()));
        
        DeliveryManagement savedDelivery = deliveryRepository.save(deliveryManagement);
        CreateDeliveryResponse response = mapper.map(savedDelivery, CreateDeliveryResponse.class);
        response.setMessage("Delivery details created successfully");
        return response;
    }

    private LocalDateTime calculateReturnDate(LocalDateTime pick_up, UrgencyType urgency) {
        // Calculate return date based on urgency type
        // Normal urgency returns in 7 days, urgent urgency returns in 3 days
        if (pick_up == null || urgency == null) {
            throw new IllegalArgumentException("Pick-up date and urgency type must not be null.");
        }
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

    @Override
    public void setLaundryManAddress(Long deliveryId, Long bookingId) {
        DeliveryManagement delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BookingNotFoundException("Delivery not found with id: " + deliveryId));

        Booking booking = bookingRepository.findBookingById(bookingId);
        if (booking == null || booking.getDeleted()== true) {
            throw new BookingNotFoundException("Booking not found with id: " + bookingId);
        }
        Optional<BookingResource> bookingResource = Optional.ofNullable(bookingResourceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking resource not found for booking id: " + bookingId)));

        if(booking.getType_of_service() == ServiceType.LAUNDRY) {
            User agent = userRepository.findUserById(bookingResource.get().getLaundryAgentId())
                    .orElseThrow(() -> new UserNotFoundException("Agent not found with id: " + bookingResource.get().getLaundryAgentId()));
            delivery.setAgentAddress(agent.getMostRecentlyUsedAddress().getId());
            deliveryRepository.save(delivery);
        }
        else {
            throw new ServiceNotFoundException("Service type not supported for delivery management.");
        }
    }
}

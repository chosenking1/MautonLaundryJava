package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BookingServiceImpl implements BookingService{
    @Autowired
    private BookingRepository bookingRepository;

    ModelMapper mapper = new ModelMapper();

    @Override
    public RegisterBookingResponse RegisterBooking(RegisterBookingRequest request) {
        Booking booking = new Booking();
        DeliveryManagementService deliveryManagementService = new DeliveryManagementServiceImpl();
        RegisterBookingResponse registerBookingResponse = new RegisterBookingResponse();
        PickupRequest pickupRequest = new PickupRequest();

//        if(userExist(request.getEmail())) {
//            throw new UserAlreadyExistsException("Email already exist");
//        }
//        else{
            booking.setFull_name(request.getFull_name());
            booking.setAddress(request.getAddress());
            booking.setEmail(request.getEmail());
            booking.setType_of_service(request.getType_of_service());
            booking.setService(String.valueOf(request.getService()));
            booking.setUrgency(request.getUrgency());
            booking.setDate_booked(request.getDate_booked());
            booking.setTotal_price(totalPriceCalculation(request.getService()));
            Booking bookingDetails = bookingRepository.save(booking);
            mapper.map(bookingDetails, pickupRequest);
            deliveryManagementService.createPickup(pickupRequest);
            mapper.map(bookingDetails, registerBookingResponse);
//    }

        return registerBookingResponse;
    }

    private Double totalPriceCalculation(JSONArray service) {
//        Double totalPrice;
        Double totalPrice = 0.0;
        for (int i = 0; i < service.length(); i++) {
            JSONObject services = service.getJSONObject(i);
            Double price = services.getDouble("price");
            totalPrice += price;
        }
        return totalPrice;
    }



    @Override
    public BookingRepository getRepository() {

        return bookingRepository;
    }

    @Override
    public ViewBookingResponse viewBooking(Long id) {
        ViewBookingResponse response = new ViewBookingResponse();
        Optional<Booking> booking = Optional.ofNullable(bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException("Service Doesnt Exist")));
        mapper.map(booking, response);
        return response;
    }

    @Override
    public ViewBookingResponse findBookingByEmail(String email) {
        ViewBookingResponse response = new ViewBookingResponse();
        Optional<Booking> bookings = Optional.of(bookingRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("Service Doesnt Exist")));
        mapper.map(bookings, response);
        return response;

    }

    @Override
    public UpdateBookingResponse bookingDetailsUpdate(UpdateBookingRequest request) {
        return null;
    }

    @Override
    public void deleteBooking(Long id) {

    }

    @Override
    public void deleteBooking(String email) {

    }
}

package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.exceptions.userexceptions.UserAlreadyExistsException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookingServiceImpl implements BookingService{
    @Autowired
    private BookingRepository bookingRepository;

    ModelMapper mapper = new ModelMapper();

    @Override
    public RegisterBookingResponse RegisterBooking(RegisterBookingRequest request) {
        Booking booking = new Booking();
        RegisterBookingResponse registerBookingResponse = new RegisterBookingResponse();

//        if(userExist(request.getEmail())) {
//            throw new UserAlreadyExistsException("Email already exist");
//        }
//        else{

            booking.setFull_name(request.getFull_name());
            booking.setAddress(request.getAddress());
            booking.setEmail(request.getEmail());
            booking.setType_of_service(request.getType_of_service());
            booking.setService(request.getService());
            booking.setDate_booked(request.getDate_booked());
            Booking bookingDetails = bookingRepository.save(booking);
            mapper.map(bookingDetails, registerBookingResponse);
//    }

        return registerBookingResponse;
    }

    @Override
    public BookingRepository getRepository() {
        return null;
    }

    @Override
    public ViewBookingResponse viewBooking(Long id) {
        return null;
    }

    @Override
    public ViewBookingResponse findBookingByEmail(String email) {
        return null;
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

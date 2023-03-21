package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;

public interface BookingService {
    RegisterBookingResponse RegisterBooking(RegisterBookingRequest request);

    BookingRepository getRepository();

    ViewBookingResponse viewBooking(Long id);
    ViewBookingResponse findBookingByEmail(String email);

    UpdateBookingResponse bookingDetailsUpdate(UpdateBookingRequest request);

    void deleteBooking(Long id);
    void deleteBooking(String email);

}

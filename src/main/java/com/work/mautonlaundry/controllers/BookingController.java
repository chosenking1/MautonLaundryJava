package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.dtos.requests.bookingrequests.RegisterBookingRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.UpdateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.RegisterBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.UpdateBookingResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.ViewBookingResponse;
import com.work.mautonlaundry.services.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;

@RestController
public class BookingController {
    @Autowired
    private BookingService bookingService;

    @PostMapping("/registerBooking")
    public RegisterBookingResponse registerBooking(@RequestBody RegisterBookingRequest request){
        return bookingService.RegisterBooking(request);
    }

    @GetMapping("/viewBooking/{id}")
    public ViewBookingResponse viewBooking(@PathVariable("id") Long id){

        return bookingService.viewBooking(id);
    }

    @GetMapping("/viewAllBooking")
    public Collection<Booking> viewAllBooking() {

        return bookingService.getRepository().findAll();
    }

    @PutMapping("/updateBooking")
    public UpdateBookingResponse updateBookingStatus(@RequestBody UpdateBookingRequest request)
    {
        return bookingService.bookingDetailsUpdate(request);
    }

    @DeleteMapping("/deleteBooking/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable("id") Long id) {
        try {
            bookingService.deleteBooking(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

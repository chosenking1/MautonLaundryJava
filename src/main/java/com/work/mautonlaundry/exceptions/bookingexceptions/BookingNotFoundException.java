package com.work.mautonlaundry.exceptions.bookingexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class BookingNotFoundException extends RuntimeException{
    private String message;
    public BookingNotFoundException(){}
    public BookingNotFoundException(String msg){
        super(msg);
        message = msg;
    }
}

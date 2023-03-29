package com.work.mautonlaundry.exceptions.bookingexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BookingAlreadyExistsException extends RuntimeException{
}

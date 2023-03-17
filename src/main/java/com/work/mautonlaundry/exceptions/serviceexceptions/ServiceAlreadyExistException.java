package com.work.mautonlaundry.exceptions.serviceexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class ServiceAlreadyExistException extends RuntimeException{
    private String message;
    public ServiceAlreadyExistException(){}

    public ServiceAlreadyExistException(String msg){
        super(msg);
        this.message = msg;
    }
}

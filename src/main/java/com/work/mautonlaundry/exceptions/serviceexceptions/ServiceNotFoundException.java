package com.work.mautonlaundry.exceptions.serviceexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ServiceNotFoundException extends RuntimeException{
    private String message;
    public ServiceNotFoundException(){}
    public ServiceNotFoundException(String msg){
        super(msg);
        message = msg;
    }
}

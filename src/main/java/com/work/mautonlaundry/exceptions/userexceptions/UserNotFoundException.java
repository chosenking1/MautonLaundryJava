package com.work.mautonlaundry.exceptions.userexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException{
    private String message;
    public UserNotFoundException(){}
    public UserNotFoundException(String msg){
        super(msg);
        message = msg;
    }
}

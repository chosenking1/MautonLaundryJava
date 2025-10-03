package com.work.mautonlaundry.exceptions.userexceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class UserAlreadyExistsException extends RuntimeException{
    private String message;
    public UserAlreadyExistsException(String msg){
        super(msg);
    }
}

package com.work.mautonlaundry.exceptions.userexceptions;

public class UserNotFoundException extends RuntimeException{
    private String message;
    public UserNotFoundException(){}
    public UserNotFoundException(String msg){
        super(msg);
        message = msg;
    }
}

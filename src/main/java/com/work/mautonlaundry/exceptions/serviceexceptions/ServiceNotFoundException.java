package com.work.mautonlaundry.exceptions.serviceexceptions;

public class ServiceNotFoundException extends RuntimeException{
    private String message;
    public ServiceNotFoundException(){}
    public ServiceNotFoundException(String msg){
        super(msg);
        message = msg;
    }
}

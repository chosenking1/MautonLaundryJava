package com.work.mautonlaundry.exceptions.serviceexceptions;

public class ServiceAlreadyExistException extends RuntimeException{
    private String message;
    public ServiceAlreadyExistException(){}

    public ServiceAlreadyExistException(String msg){
        super(msg);
        this.message = msg;
    }
}

package com.work.mautonlaundry.dtos.responses;

public class ErrorResponse {
    private String code;
    private String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    // Getters and setters (or use Lombok)
}


package com.work.mautonlaundry.dtos.responses.serviceresponse;

import lombok.Data;

@Data
public class ServiceResponse {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Double price;
    private Double white;
}
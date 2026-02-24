package com.work.mautonlaundry.dtos.requests.addressrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAddressRequest {
    
    @NotBlank(message = "Street is required")
    private String street;
    
    private Integer streetNumber;
    
    @NotBlank(message = "City is required")
    private String city;
    
    private String state;
    private String zip;
    private String country;
    private Double latitude;
    private Double longitude;
}
package com.work.mautonlaundry.dtos.requests.addressrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAddressRequest {
    
    @NotBlank(message = "Street is required")
    private String street;
    
    @NotNull(message = "Street number is required")
    private Integer streetNumber;
    
    @NotBlank(message = "City is required")
    private String city;
    
    @NotBlank(message = "State is required")
    private String state;
    
    @NotBlank(message = "ZIP code is required")
    private String zip;
    
    @NotBlank(message = "Country is required")
    private String country;
    
    private Double latitude;
    private Double longitude;
}
package com.work.mautonlaundry.dtos.requests.addressrequests;

import lombok.Data;

@Data
public class UpdateAddressRequest {
    
    private String street;
    private Integer streetNumber;
    private String city;
    private String state;
    private String zip;
    private String country;
    private Double latitude;
    private Double longitude;
}
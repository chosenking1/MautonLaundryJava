package com.work.mautonlaundry.dtos.requests.servicerequests;

import com.work.mautonlaundry.data.model.ServiceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;

@Setter
@Getter
@AllArgsConstructor
public class AddServiceRequest {

    private String service_name;
    private String service_category;
    private String service_details;
    private ServiceType type_of_service;
    private int service_price;
    private int service_price_white;
    private String photos;
}

package com.work.mautonlaundry.dtos.responses.serviceresponse;

import com.work.mautonlaundry.data.model.ServicePrice;
import com.work.mautonlaundry.data.model.ServiceType;
import lombok.Data;



@Data
public class UpdateServiceResponse {
    private Long id;
    private String service_name;
    private String service_category;
    private String service_details;
    private ServiceType type_of_service;
    private ServicePrice servicePrice;
    private String photos;
}

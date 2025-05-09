package com.work.mautonlaundry.dtos.responses.serviceresponse;

import com.work.mautonlaundry.data.model.ServiceType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateServiceResponse {
    private Long id;
    private String service_name;
    private String service_category;
    private String service_details;
    private ServiceType type_of_service;
    private int service_price;
    private int service_price_white;
    private String photos;
}

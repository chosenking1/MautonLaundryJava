package com.work.mautonlaundry.dtos.requests.servicerequests;

import com.work.mautonlaundry.data.model.ServiceType;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceRequest {
    private Long id;
    private String service_name;
    private String service_category;
    private String service_details;
    private ServiceType type_of_service;
    private int service_price;
    private int service_price_white;
    private int locationMultiplier;
    private int express;
    private String photos;
}

package com.work.mautonlaundry.dtos.responses.serviceresponse;

import com.work.mautonlaundry.data.model.ServicePricing;
import lombok.Data;

import java.util.List;

@Data
public class ServiceResponse {
    private Long id;
    private String name;
    private String description;
    private String categoryName;
    private String imagePath;
    private List<ServicePricing> pricing;
}
package com.work.mautonlaundry.dtos.requests.servicerequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateServiceRequest {
    @NotBlank
    private String name;
    
    private String description;
    
    @NotNull
    private Long categoryId;
    
    private String imagePath;
    
    private String estimatedDuration;
}
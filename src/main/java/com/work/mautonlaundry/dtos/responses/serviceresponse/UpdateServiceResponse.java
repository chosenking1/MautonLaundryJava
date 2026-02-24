package com.work.mautonlaundry.dtos.responses.serviceresponse;

import lombok.Data;

@Data
public class UpdateServiceResponse {
    private Long id;
    private String name;
    private String description;
    private String categoryName;
    private String imagePath;
}

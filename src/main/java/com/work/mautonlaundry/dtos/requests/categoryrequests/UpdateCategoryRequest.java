package com.work.mautonlaundry.dtos.requests.categoryrequests;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String description;
    private String imagePath;
}
package com.work.mautonlaundry.dtos.responses.serviceresponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddServiceResponse {
    private Long id;
    private String name;
    private String description;
    private String categoryName;
    private String imagePath;
}

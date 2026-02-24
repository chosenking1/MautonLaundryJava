package com.work.mautonlaundry.dtos.requests.servicerequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddServiceRequest {
    private String name;
    private String description;
    private Long categoryId;
    private String imagePath;
}

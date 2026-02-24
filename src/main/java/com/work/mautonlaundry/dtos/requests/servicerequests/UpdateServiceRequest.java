package com.work.mautonlaundry.dtos.requests.servicerequests;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateServiceRequest {
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String imagePath;
    private String estimatedDuration;
}

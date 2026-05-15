package com.work.mautonlaundry.dtos.requests.handoffrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegenerateHandoffCodeRequest {

    @NotBlank(message = "stage is required")
    private String stage;
}

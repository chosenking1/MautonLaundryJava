package com.work.mautonlaundry.dtos.requests.agentapplications;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateLaundryApplicationRequest {
    @NotEmpty(message = "At least one address is required")
    private List<String> addressIds;
}

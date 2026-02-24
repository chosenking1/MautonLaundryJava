package com.work.mautonlaundry.dtos.requests.rolerequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Role name is required")
    private String name;

    private String description;
}

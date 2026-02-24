package com.work.mautonlaundry.dtos.requests.rolechangerequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleChangeRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Requested role is required")
    private String requestedRole;
}
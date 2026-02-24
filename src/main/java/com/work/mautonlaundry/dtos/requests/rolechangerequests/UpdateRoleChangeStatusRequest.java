package com.work.mautonlaundry.dtos.requests.rolechangerequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRoleChangeStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
}
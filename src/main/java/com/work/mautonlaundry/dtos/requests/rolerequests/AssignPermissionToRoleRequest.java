package com.work.mautonlaundry.dtos.requests.rolerequests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignPermissionToRoleRequest {
    @NotNull(message = "Role ID is required")
    private Long roleId;

    @NotNull(message = "Permission ID is required")
    private Long permissionId;
}

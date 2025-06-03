package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.data.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateUserRoleRequest {
    private String userId;
    private UserRole role;
}

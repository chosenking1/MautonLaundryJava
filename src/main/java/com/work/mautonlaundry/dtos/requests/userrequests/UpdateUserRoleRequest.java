package com.work.mautonlaundry.dtos.requests.userrequests;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateUserRoleRequest {
    private String userId;
    private String roleName;
}
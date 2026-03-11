package com.work.mautonlaundry.dtos.responses.permission;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UserPermissionsResponse {
    private String role;
    private List<String> permissions;
    private Map<String, Boolean> pages;
}

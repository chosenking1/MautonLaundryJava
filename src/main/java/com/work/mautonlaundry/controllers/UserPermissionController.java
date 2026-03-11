package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.responses.permission.UserPermissionsResponse;
import com.work.mautonlaundry.services.UserPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserPermissionController {
    
    private final UserPermissionService userPermissionService;
    
    @GetMapping("/permissions")
    public ResponseEntity<UserPermissionsResponse> getUserPermissions() {
        return ResponseEntity.ok(userPermissionService.getUserPermissions());
    }
}

package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.UserPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserPermissionController {
    
    private final UserPermissionService userPermissionService;
    
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getUserPermissions() {
        Map<String, Object> permissions = userPermissionService.getUserPermissions();
        return ResponseEntity.ok(permissions);
    }
}

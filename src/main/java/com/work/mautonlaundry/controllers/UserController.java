package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository userRepository;

    @GetMapping("/getUser/{email}")
    @PreAuthorize("hasAuthority('USER_READ') or #email == authentication.principal.username")
    public ResponseEntity<AppUser> getUserByEmail(@PathVariable String email) {
        AppUser appUser = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(appUser);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_READ') or #userId == authentication.principal.username")
    public ResponseEntity<AppUser> getUserById(@PathVariable String userId) {
        AppUser appUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(appUser);
    }

    @GetMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('USER_READ') or #userId == authentication.principal.username")
    public ResponseEntity<Map<String, Object>> getUserRole(@PathVariable String userId) {
        AppUser appUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Access the single role directly
        String roleName = appUser.getRole() != null ? appUser.getRole().getName() : "NO_ROLE";
        
        Map<String, Object> response = Map.of(
            "userId", appUser.getId(),
            "role", roleName, // Changed from "roles" to "role" and now a single string
            "roleStatus", "ACTIVE"
        );
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE') or #userId == authentication.principal.username")
    public ResponseEntity<Map<String, String>> updateUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        
        AppUser appUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Users cannot change their own role
        if (request.containsKey("role")) {
            throw new RuntimeException("Role changes not allowed through this endpoint");
        }
        
        if (request.containsKey("fullName")) {
            appUser.setFull_name(request.get("fullName"));
        }
        if (request.containsKey("phoneNumber")) {
            appUser.setPhone_number(request.get("phoneNumber"));
        }
        
        appUser.setAddress(appUser.getAddress()); // Keeping this line as it was in the original code
        
        userRepository.save(appUser);
        
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }
}
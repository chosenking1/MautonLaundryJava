package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.security.PermissionEvaluationService;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserProfileRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.CurrentUserResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final com.work.mautonlaundry.services.TermsService termsService;
    
    private final UserRepository userRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        AppUser currentUser = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(currentUser.getId());
        response.setEmail(currentUser.getEmail());
        response.setFullName(currentUser.getFull_name());
        response.setPhoneNumber(currentUser.getPhone_number());
        response.setRole(currentUser.getRole() != null ? currentUser.getRole().getName() : null);
        // Effective permissions, so the admin portal can gate pages by permission
        // (the dimension the API enforces) rather than by role alone.
        response.setPermissions(permissionEvaluationService.getEffectivePermissions(currentUser.getId()));
        response.setCurrentTermsVersion(termsService.currentVersion());
        response.setTermsAcceptanceRequired(termsService.needsAcceptance(currentUser));
        response.setIsFirstLogin(currentUser.getIsFirstLogin());
        response.setEmailVerified(currentUser.getEmailVerified());
        response.setAddresses(currentUser.getAddresses());
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<MessageResponse> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        
        if (request.getFullName() != null) {
            currentUser.setFull_name(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            currentUser.setPhone_number(request.getPhoneNumber());
        }
        
        userRepository.save(currentUser);
        
        return ResponseEntity.ok(new MessageResponse("Profile updated successfully"));
    }
}

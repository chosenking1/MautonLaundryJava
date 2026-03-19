package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserRoleRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

public interface UserService {

    RegisterUserResponse registerUser(RegisterUserRequest request);

    UserRepository getRepository();

    FindUserResponse findUserByEmail(String userEmail);



    FindUserResponse findUserById(String id);

    void deleteUserByEmail(String email);

    void deleteUserById(String id);

    UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest request);

    void updateUserRole(UpdateUserRoleRequest request);
    
    // Get all users with pagination
    Page<FindUserResponse> getAllUsers(Pageable pageable);
    
    // Email verification methods
    void sendEmailVerification(String email);
    boolean verifyEmail(String token);
    
    // Password reset methods
    void sendPasswordResetEmail(String email);
    boolean resetPassword(String token, String newPassword);
    
    // Current user method
    FindUserResponse getCurrentUser();

    void deactivateAgent(String userId, String reason);
}

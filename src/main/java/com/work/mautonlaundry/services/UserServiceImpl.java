package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.model.UserRole;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserRoleRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;
import com.work.mautonlaundry.exceptions.userexceptions.UserAlreadyExistsException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.service.AuthService;

import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.persistence.Cacheable;
import jakarta.validation.Valid;
import org.apache.commons.logging.Log;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {


    private final UserRepository userRepository;
    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final PasswordEncoder passwordEncoder;

    private final ModelMapper mapper;

//    @Autowired
//    private AuditService auditService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, @Lazy AuthService authService,
                           PasswordEncoder passwordEncoder, ModelMapper mapper) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Override
    @Validated
    @Transactional
    public RegisterUserResponse registerUser(@Valid RegisterUserRequest request) {
        User user = new User();
        RegisterUserResponse registerResponse = new RegisterUserResponse();

        if(userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exist");
        }
        else{
        user.setFull_name(request.getFirstname() +" "+ request.getSecond_name());
        user.setAddress(request.getAddress());
        user.setEmail(request.getEmail());
        user.setUserRole(UserRole.USER);
        user.setPassword(setPassword(request.getPassword()));
        user.setPhone_number(request.getPhone_number());
        User userDetails = userRepository.save(user);

//        mapper.map(userDetails, registerResponse);
            registerResponse.setEmail(userDetails.getEmail());
        }

        return registerResponse;
    }

    @Override
    public UserRepository getRepository() {
        return userRepository;
    }

    @Override
    public FindUserResponse findUserByEmail(String userEmail) {
        FindUserResponse response = new FindUserResponse();
        userEmail = userEmail.toLowerCase();
        User user = userRepository.findUserByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User Doesn't Exist"));
        mapper.map(user, response);
        return response;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                authorities);
    }

//    private Set<SimpleGrantedAuthority> getAuthority(User user) {
//        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
//        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));
//        return authorities;
//    }

    @Override
    public FindUserResponse findUserById(String id) {
        FindUserResponse response = new FindUserResponse();
        User user = userRepository.findUserById(id)
                .orElseThrow(() -> new UserNotFoundException("User Doesn't Exist"));
        mapper.map(user, response);
        return response;
    }

    private Boolean userEmailExist(String email) {
        return userRepository.findUserByEmail(email).isPresent();
    }


    private Boolean userIdExist(String id){
        return userRepository.findUserById(id).isPresent();
    }

    private String setPassword(String password) {

        return passwordEncoder.encode(password);

    }


    private void deleteUser(User user) {
        if(isLoggedInUserAccount(user.getId()) || userIsAdmin()){
            User deletedUser = userRepository.findUserById(user.getId())
                    .orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));

            deletedUser.setDeleted(true);
            userRepository.save(deletedUser);}
//        } else if (userIsAdmin()) {
//            User deletedUser = userRepository.findUserById(user.getId())
//                    .orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));
//
//            deletedUser.setDeleted(true);
//            userRepository.save(deletedUser);
//        }
        else {
            throw new AccessDeniedException("User not permitted to perform this operation");
        }

    }

    private User loggedInUser() {
        // Get current authenticated user
//        return authService.getCurrentAuthenticatedUser()
//                .orElseThrow(() -> new SecurityException("User not authenticated"));

        return SecurityUtil.getCurrentUser().orElseThrow(() -> new SecurityException("User not authenticated"));

    }

    private Boolean isLoggedInUserAccount(String id){
        return loggedInUser().getId().equals(id);
    }
    private Boolean userIsAdmin(){
        return loggedInUser().getUserRole().equals(UserRole.ADMIN);
    }
    @Override
    public void deleteUserById(String id) {
        User user = userRepository.findUserById(id).orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));
        deleteUser(user);
    }
    @Override
//    @Cacheable(value = "users", key = "#email")
    public  void deleteUserByEmail(String email) {
        User user = userRepository.findUserByEmail(email).orElseThrow(()-> new UserNotFoundException("User Doesnt Exist"));
        deleteUser(user);

    }

    @Override
    @Transactional
    public UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest user) {
        UpdateUserDetailResponse updateResponse = new UpdateUserDetailResponse();

        // Check if the user exists
        if (isLoggedInUserAccount(user.getId()) || userIsAdmin()) {

                User existingUser = userRepository.findUserById(user.getId())
                        .orElseThrow(() -> new UserNotFoundException("User doesn't exist"));;

                // Update only the necessary fields
                existingUser.setFull_name(user.getFirstname() + " " + user.getSecond_name());
                existingUser.setAddress(user.getAddress());
                existingUser.setPhone_number(user.getPhone_number());

                // Save the updated user
                userRepository.save(existingUser);

                String message = "Details Updated Successfully";
                mapper.map(message, updateResponse);
                return updateResponse;
            }

        else {throw new AccessDeniedException("User not permitted to perform this operation");}
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserRole(UpdateUserRoleRequest request) {
        // Verify admin privileges
        if (!userIsAdmin()) {
            throw new AccessDeniedException("Only ADMIN can update user roles");
        }else {
            // Find target user
            User targetUser = userRepository.findUserById(request.getUserId())
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + request.getUserId()));

            // Update role
            targetUser.setUserRole(request.getRole());
            userRepository.save(targetUser);

            // Optional: Log the role change
            log.info("User {} role changed to {} by admin {} ",
                    targetUser.getFull_name(), request.getRole(), loggedInUser().getFull_name());
        }

    }




}

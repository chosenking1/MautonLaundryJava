package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.Permission;
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.model.VerificationToken; // Corrected import
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.VerificationTokenRepository;
import com.work.mautonlaundry.util.TokenGenerator;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.CacheManager;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService, UserDetailsService {


    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final TokenGenerator tokenGenerator;
    private final CacheManager cacheManager;
    private final AuthService authService;

    private final PasswordEncoder passwordEncoder;

    private final ModelMapper mapper;

    @Value("${app.token.verification.expiry-hours:24}")
    private int verificationTokenExpiryHours;

    @Value("${app.token.password-reset.expiry-hours:1}")
    private int passwordResetTokenExpiryHours;

    @Autowired
    private AuditService auditService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, AddressRepository addressRepository, RoleRepository roleRepository, VerificationTokenRepository tokenRepository,
                           EmailService emailService, TokenGenerator tokenGenerator, CacheManager cacheManager,
                           @Lazy AuthService authService, PasswordEncoder passwordEncoder, ModelMapper mapper) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.tokenGenerator = tokenGenerator;
        this.cacheManager = cacheManager;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Override
    @Validated
    @Transactional
    public RegisterUserResponse registerUser(@Valid RegisterUserRequest request) {
        AppUser user = new AppUser();
        RegisterUserResponse registerResponse = new RegisterUserResponse();

        if(userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exist");
        }
        else{
        user.setFull_name(request.getFirstname() +" "+ request.getSecond_name());
        user.setEmail(request.getEmail());
        
        // Assign default role
        Role customerRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default role 'CUSTOMER' not found in database"));
        user.setRole(customerRole);
        
        user.setPassword(setPassword(request.getPassword()));
        user.setPhone_number(request.getPhone_number());
        
        // Save user first
        AppUser savedUser = userRepository.save(user);
        
        // Create address only if street or city is provided
        if ((request.getStreet() != null && !request.getStreet().trim().isEmpty()) || 
            (request.getCity() != null && !request.getCity().trim().isEmpty())) {
            Address address = new Address();
            address.setUser(savedUser);
            address.setStreet(request.getStreet());
            address.setStreet_number(request.getStreetNumber());
            address.setCity(request.getCity());
            address.setState(request.getState());
            address.setZip(request.getZip());
            address.setCountry(request.getCountry());
            address.setLatitude(request.getLatitude());
            address.setLongitude(request.getLongitude());
            address.setIsDefault(true);
            address.setDeleted(false);
            
            addressRepository.save(address);
        }

        auditService.logAction("CREATE", "USER", savedUser.getEmail());
        registerResponse.setEmail(savedUser.getEmail());
        registerResponse.setMessage("Registration successful. Please check your email for verification.");
        
        // Send email verification
        sendEmailVerification(savedUser.getEmail());
        }

        return registerResponse;
    }

    @Override
    public UserRepository getRepository() {
        return userRepository;
    }

    @Override
    @Cacheable(value = "users", key = "#userEmail.toLowerCase()")
    public FindUserResponse findUserByEmail(String userEmail) {
        FindUserResponse response = new FindUserResponse();
        userEmail = userEmail.toLowerCase();
        AppUser user = userRepository.findUserByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User Doesn't Exist"));
        mapper.map(user, response);
        return response;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findUserByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        log.debug("Loaded user: {} with password {}", username, user.getPassword()); // Added logging
        log.debug("Password length: {}", user.getPassword().length());

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRole() != null) {
            // Add role as an authority, e.g., "ROLE_ADMIN"
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
            // Add all permissions associated with the role, e.g., "USER_READ", "USER_CREATE"
            for (Permission permission : user.getRole().getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getName()));
            }
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                authorities);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public FindUserResponse findUserById(String id) {
        FindUserResponse response = new FindUserResponse();
        AppUser user = userRepository.findUserById(id)
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


    private void deleteUser(AppUser user) {
        if(isLoggedInUserAccount(user.getId()) || userIsAdmin()){
            AppUser deletedUser = userRepository.findUserById(user.getId())
                    .orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));

            deletedUser.setDeleted(true);
            userRepository.save(deletedUser);}
        else {
            throw new AccessDeniedException("User not permitted to perform this operation");
        }

    }

    private AppUser loggedInUser() {
        return SecurityUtil.getCurrentUser().orElseThrow(() -> new SecurityException("User not authenticated"));
    }

    private Boolean isLoggedInUserAccount(String id){
        return loggedInUser().getId().equals(id);
    }
    
    private Boolean userIsAdmin(){
        AppUser loggedIn = loggedInUser();
        return loggedIn.getRole() != null && loggedIn.getRole().getName().equals("ADMIN");
    }
    
    @Override
    @CacheEvict(value = "users", key = "#id")
    public void deleteUserById(String id) {
        AppUser user = userRepository.findUserById(id).orElseThrow(() -> new UserNotFoundException("User Doesnt Exist"));
        deleteUser(user);
        auditService.logAction("DELETE", "USER", id);
    }
    @Override
    @CacheEvict(value = "users", key = "#email.toLowerCase()")
    public void deleteUserByEmail(String email) {
        AppUser user = userRepository.findUserByEmail(email).orElseThrow(()-> new UserNotFoundException("User Doesnt Exist"));
        deleteUser(user);

    }

    @Override
    @Transactional
    public UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest user) {
        UpdateUserDetailResponse updateResponse = new UpdateUserDetailResponse();

        // Check if the user exists
        if (isLoggedInUserAccount(user.getId()) || userIsAdmin()) {

                AppUser existingUser = userRepository.findUserById(user.getId())
                        .orElseThrow(() -> new UserNotFoundException("User doesn't exist"));;

                // Update only the necessary fields
                existingUser.setFull_name(user.getFirstname() + " " + user.getSecond_name());
                existingUser.setPhone_number(user.getPhone_number());

                // Save the updated user
                userRepository.save(existingUser);
                auditService.logAction("UPDATE", "USER", user.getId());

                String message = "Details Updated Successfully";
                mapper.map(message, updateResponse);
                return updateResponse;
            }

        else {throw new AccessDeniedException("User not permitted to perform this operation");}
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')") // Changed from hasRole('ADMIN')
    public void updateUserRole(UpdateUserRoleRequest request) {
        // This method should be deprecated or updated to use RoleChangeRequest
        // For now, we'll implement direct update for ADMINs as a fallback
        AppUser user = userRepository.findUserById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
        user.setRole(role);

        userRepository.save(user);
    }

    @Override
    public void sendEmailVerification(String email) {
        log.info("Starting email verification process for: {}", email);
        
        AppUser user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        String token = tokenGenerator.generateSecureToken();
        VerificationToken verificationToken = new VerificationToken(token, user, VerificationToken.TokenType.EMAIL_VERIFICATION, verificationTokenExpiryHours);
        tokenRepository.save(verificationToken);
        
        log.info("Sending verification email to: {} with token: {}", email, token);
        emailService.sendVerificationEmail(email, token);
        log.info("Verification email sent successfully to: {}", email);
    }

    @Override
    @Transactional
    public boolean verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElse(null);
        
        if (verificationToken == null || verificationToken.isExpired() || verificationToken.isUsed()) {
            return false;
        }
        
        AppUser user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        
        // Manual cache eviction
        cacheManager.getCache("users").evict(user.getEmail().toLowerCase());
        
        verificationToken.setUsed(true);
        tokenRepository.save(verificationToken);
        
        return true;
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        AppUser user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        String token = tokenGenerator.generateSecureToken();
        VerificationToken resetToken = new VerificationToken(token, user, VerificationToken.TokenType.PASSWORD_RESET, passwordResetTokenExpiryHours);
        tokenRepository.save(resetToken);
        
        emailService.sendPasswordResetEmail(email, token);
    }

    @Override
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        VerificationToken resetToken = tokenRepository.findByToken(token)
                .orElse(null);
        
        if (resetToken == null || resetToken.isExpired() || resetToken.isUsed()) {
            return false;
        }
        
        // Validate password
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 15) {
            throw new IllegalArgumentException("Password must be between 6 and 15 characters");
        }
        
        AppUser user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        // Manual cache eviction
        cacheManager.getCache("users").evict(user.getEmail().toLowerCase());
        
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        
        return true;
    }

    @Override
    public Page<FindUserResponse> getAllUsers(Pageable pageable) {
        Page<AppUser> users = userRepository.findByDeletedFalse(pageable);
        return users.map(user -> {
            FindUserResponse response = new FindUserResponse();
            mapper.map(user, response);
            // Add role information
            if (user.getRole() != null) {
                response.setUserRole(user.getRole().getName());
            }
            return response;
        });
    }
    
    @Override
    public FindUserResponse getCurrentUser() {
        AppUser currentUser = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        FindUserResponse response = new FindUserResponse();
        mapper.map(currentUser, response);
        if (currentUser.getRole() != null) {
            response.setUserRole(currentUser.getRole().getName());
        }
        return response;
    }
}

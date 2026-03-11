package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.PasswordResetRequest;
import com.work.mautonlaundry.dtos.requests.VerifyEmailRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest; // Import RegisterUserRequest
import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse; // Import RegisterUserResponse
import com.work.mautonlaundry.dtos.responses.userresponse.UserLoginResponse;
import com.work.mautonlaundry.security.service.AuthService;
import com.work.mautonlaundry.services.AuditService;
import com.work.mautonlaundry.services.UserService;
import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private AuditService auditService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());
        try {
            RegisterUserResponse response = userService.registerUser(request);
            auditService.logAction("REGISTER", "AUTH", request.getEmail());
            log.info("Registration successful for email: {}", request.getEmail());
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            auditService.logAction("REGISTER_FAILED", "AUTH", request.getEmail());
            log.warn("Registration failed for email: {} - {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        try {
            String token = authService.login(request);
            UserLoginResponse response = new UserLoginResponse(token, "Bearer");
            auditService.logAction("LOGIN", "AUTH", request.getEmail());
            log.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            auditService.logAction("LOGIN_FAILED", "AUTH", request.getEmail());
            log.warn("Login failed for email: {} - {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        log.info("User logout requested");
        auditService.logAction("LOGOUT", "AUTH");
        SecurityContextHolder.clearContext();
        log.info("User logged out successfully");
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/send-verification")
    public ResponseEntity<String> sendEmailVerification(@RequestParam @NotBlank String email) {
        log.info("Email verification requested");
        userService.sendEmailVerification(email);
        log.info("Email verification sent successfully");
        return ResponseEntity.ok("Verification email sent");
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmailGet(@RequestParam String token) {
        log.info("Email verification attempt with token via GET");
        boolean verified = userService.verifyEmail(token);
        if (verified) {
            log.info("Email verification successful");
            return ResponseEntity.ok("Email verified successfully. You can now log in.");
        }
        log.warn("Email verification failed - invalid or expired token");
        return ResponseEntity.badRequest().body("Invalid or expired token");
    }

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Email verification attempt with token");
        boolean verified = userService.verifyEmail(request.getToken());
        if (verified) {
            log.info("Email verification successful");
            return ResponseEntity.ok("Email verified successfully");
        }
        log.warn("Email verification failed - invalid or expired token");
        return ResponseEntity.badRequest().body("Invalid or expired token");
    }

    @GetMapping("/reset-password")
    public ResponseEntity<String> resetPasswordGet(@RequestParam String token) {
        log.info("Password reset page accessed with token via GET");
        return ResponseEntity.ok("Token valid. Use POST /api/auth/reset-password with the token to set new password.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam @NotBlank String email) {
        log.info("Password reset requested");
        userService.sendPasswordResetEmail(email);
        log.info("Password reset email sent successfully");
        return ResponseEntity.ok("Password reset email sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset attempt with token");
        boolean reset = userService.resetPassword(request.getToken(), request.getNewPassword());
        if (reset) {
            log.info("Password reset successful");
            return ResponseEntity.ok("Password reset successfully");
        }
        log.warn("Password reset failed - invalid or expired token");
        return ResponseEntity.badRequest().body("Invalid or expired token");
    }
}

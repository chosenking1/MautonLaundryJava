package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.UserLoginResponse;
import com.work.mautonlaundry.security.service.AuthService;
import com.work.mautonlaundry.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private AuthService authService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest loginDto){
        String token = null;
        try {
            token = authService.login(loginDto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserLoginResponse response = new UserLoginResponse();
        response.setAccessToken(token);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

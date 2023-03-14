package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;
import com.work.mautonlaundry.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public RegisterUserResponse registerUser(@RequestBody RegisterUserRequest request){
        return userService.registerUser(request);
    }

    @GetMapping("/getUser/{email}")
    public FindUserResponse findUserUsingEmail(@PathVariable("email") String email){
        return userService.findUserByEmail(email);
    }

    @PutMapping("/updateUser")
    public UpdateUserDetailResponse
    updateUserDetails(@RequestBody UpdateUserDetailRequest request)
    {
        return userService.userDetailsUpdate(request);
    }

}

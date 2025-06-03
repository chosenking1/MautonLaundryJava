package com.work.mautonlaundry.security.service;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;

import java.util.Optional;

public interface AuthService {
    String login(UserLoginRequest loginRequest) throws Exception;

    Optional<User> getCurrentAuthenticatedUser();
}

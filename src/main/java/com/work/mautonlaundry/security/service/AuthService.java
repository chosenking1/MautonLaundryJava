package com.work.mautonlaundry.security.service;

import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;

public interface AuthService {
    String login(UserLoginRequest loginRequest) throws Exception;
}

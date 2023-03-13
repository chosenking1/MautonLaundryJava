package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;

public interface UserService {

    RegisterUserResponse registerUser(RegisterUserRequest request);

    UserRepository getRepository();

    FindUserResponse findUserByEmail(String userEmail);

    void deleteUserByEmail(User user);

    UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest request);



}

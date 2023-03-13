package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService{
@Autowired
private UserRepository userRepository;

ModelMapper mapper = new ModelMapper();

    @Override
    public RegisterUserResponse registerUser(RegisterUserRequest request) {
        User user = new User();
        RegisterUserResponse registerResponse = new RegisterUserResponse();
        user.setFull_name(request.getFirstname() +" "+ request.getSecond_name());
        user.setAddress(request.getAddress());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setPhone_number(request.getPassword());
        User userDetails = userRepository.save(user);
        mapper.map(userDetails, registerResponse);

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
        Optional<User> user = userRepository.findUserByEmail(userEmail);
        mapper.map(user, response);
        return response;
    }

    @Override
    public void deleteUserByEmail(User user) {
        userRepository.delete(user);
    }

    @Override
    public UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest request) {
        return null;
    }
}

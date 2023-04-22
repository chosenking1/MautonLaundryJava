package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.model.UserRole;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.requests.userrequests.UpdateUserDetailRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.FindUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.dtos.responses.userresponse.UpdateUserDetailResponse;
import com.work.mautonlaundry.exceptions.userexceptions.UserAlreadyExistsException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {
@Autowired
private UserRepository userRepository;

ModelMapper mapper = new ModelMapper();

    @Override
    public RegisterUserResponse registerUser(RegisterUserRequest request) {
        User user = new User();
        RegisterUserResponse registerResponse = new RegisterUserResponse();

        if(userExist(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exist");
        }
        else{
        user.setFull_name(request.getFirstname() +" "+ request.getSecond_name());
        user.setAddress(request.getAddress());
        user.setEmail(request.getEmail());
        user.setUserRole(UserRole.USER);
        user.setPassword(setPassword(request.getPassword()));
        user.setPhone_number(request.getPassword());
        User userDetails = userRepository.save(user);

        mapper.map(userDetails, registerResponse);}

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
        Optional<User> user = Optional.ofNullable(userRepository.findUserByEmail(userEmail).orElseThrow(() -> new UserNotFoundException("Customer Doesnt Exist")));
        mapper.map(user, response);
        return response;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));

        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), authorities);
    }

    private Set<SimpleGrantedAuthority> getAuthority(User user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));
        return authorities;
    }

    @Override
    public FindUserResponse findUserById(Long id) {
        FindUserResponse response = new FindUserResponse();

        Optional<User> user = Optional.ofNullable(userRepository.findUserById(id).orElseThrow(() -> new UserNotFoundException("Customer Doesnt Exist")));
        mapper.map(user, response);
        return response;
    }

    private Boolean userExist(String email){
        return findUserByEmail(email) != null;
    }

    private Boolean userExist(Long id){
        return findUserById(id) != null;
    }

    private String setPassword(String password) {
//        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
//        return passwordEncoder.encode(password);
      return BCrypt.hashpw(password, BCrypt.gensalt());

    }

    @Override
    public void deleteUserByEmail(User user) {
        userRepository.delete(user);
    }

    @Override
    public UpdateUserDetailResponse userDetailsUpdate(UpdateUserDetailRequest user) {
        User existingUser = new User();
        UpdateUserDetailResponse updateResponse = new UpdateUserDetailResponse();

        if(userExist(user.getId())) {

            existingUser.setFull_name(user.getFirstname() +" "+ user.getSecond_name());
            existingUser.setAddress(user.getAddress());
            existingUser.setPhone_number(user.getPhone_number());
             userRepository.save(existingUser);
            String message = "Details Updated Successfully";
            mapper.map(message, updateResponse);
            return updateResponse;
        }
        else{

            throw new UserNotFoundException("User doesn't exist");

        }

        //Todo Admin/Delivery/Laundry Agent should change there password after being registered by the Superadmin
        //Todo Only this guys should be able to access the link
        //Todo user should also be able to do that but that is done by them
        //Todo Only admins can change user role


    }
}

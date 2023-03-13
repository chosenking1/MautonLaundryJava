package com.work.mautonlaundry.dtos.requests.userrequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;

@Setter
@Getter
@AllArgsConstructor
public class RegisterUserRequest {
    private String firstname;
    private String second_name;

    @Email
    private String email;

    private String phone_number;

    private String password;

    private String address;

}

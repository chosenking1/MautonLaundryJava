package com.work.mautonlaundry.dtos.requests.userrequests;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;



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

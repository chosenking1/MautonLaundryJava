package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;



@Setter
@Getter
@AllArgsConstructor
public class RegisterUserRequest {
    private String firstname;
    private String second_name;

    @ValidEmail
    @NotBlank(message = "Email is required")
    private String email;

    private String phone_number;

    private String password;

    private String address;

}

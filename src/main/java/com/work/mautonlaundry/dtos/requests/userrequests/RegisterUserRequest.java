package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterUserRequest {
    @NotBlank(message = "First name is required")
    private String firstname;
    
    @NotBlank(message = "Last name is required")
    private String second_name;

    @ValidEmail
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone_number;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    // Optional address fields
    private String street;
    private Integer streetNumber;
    private String city;
    private String state;
    private String zip;
    private String country;
    private Double latitude;
    private Double longitude;
}

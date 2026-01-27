package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {
    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;
    
    @NotBlank(message = "Password is required")
    private String password;
}

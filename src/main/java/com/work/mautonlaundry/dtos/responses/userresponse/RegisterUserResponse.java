package com.work.mautonlaundry.dtos.responses.userresponse;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterUserResponse {
    private String email;
    private String message; // Added message field
}

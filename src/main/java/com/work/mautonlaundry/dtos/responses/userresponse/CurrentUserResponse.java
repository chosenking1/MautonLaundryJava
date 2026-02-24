package com.work.mautonlaundry.dtos.responses.userresponse;

import com.work.mautonlaundry.data.model.Address;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserResponse {
    private String id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private Boolean isFirstLogin;
    private Boolean emailVerified;
    private List<Address> addresses;
}
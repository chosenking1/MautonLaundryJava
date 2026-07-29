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
    /**
     * The user's effective permissions (Permission Architecture V2). Lets the
     * admin portal gate pages by permission rather than role -- the same
     * dimension the API enforces -- so a non-admin who holds a permission can
     * reach the page for it.
     */
    private java.util.Set<String> permissions;
    private Boolean isFirstLogin;
    private Boolean emailVerified;
    private List<Address> addresses;
}
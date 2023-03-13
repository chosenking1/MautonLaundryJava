package com.work.mautonlaundry.dtos.responses.userresponse;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FindUserResponse {
    private Long id;
    private String email;
    private String full_name;
    private String address;
    private String phone_number;
}

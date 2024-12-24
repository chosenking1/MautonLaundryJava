package com.work.mautonlaundry.dtos.requests.userrequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
public class UpdateUserDetailRequest {
    private String id;
    private String phone_number;
    private String address;
    private String firstname;
    private String second_name;
}

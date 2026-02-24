package com.work.mautonlaundry.dtos.requests.userrequests;

import lombok.Data;

@Data
public class UpdateUserProfileRequest {
    private String fullName;
    private String phoneNumber;
}
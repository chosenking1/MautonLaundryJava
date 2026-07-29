package com.work.mautonlaundry.dtos.requests.userrequests;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {
    // Fields are optional (null = leave unchanged) but when present must be
    // well-formed -- same rules as registration, so the profile-update path
    // cannot smuggle in what registration rejects.
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Name must contain only letters")
    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Enter a valid phone number")
    private String phoneNumber;
}
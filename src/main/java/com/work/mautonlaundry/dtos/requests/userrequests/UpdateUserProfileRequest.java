package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.PhoneNumbers;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {
    // Fields are optional (null = leave unchanged) but when present must be
    // well-formed -- same rules as registration, so the profile-update path
    // cannot smuggle in what registration rejects.
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Name must contain only letters")
    private String fullName;

    @Pattern(regexp = PhoneNumbers.NIGERIAN_MOBILE_REGEX, message = PhoneNumbers.NIGERIAN_MOBILE_MESSAGE)
    private String phoneNumber;
}
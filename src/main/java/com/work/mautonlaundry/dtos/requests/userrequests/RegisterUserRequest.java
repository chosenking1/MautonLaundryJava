package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import com.work.mautonlaundry.util.PhoneNumbers;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterUserRequest {
    // Letters (any script), plus space, hyphen, apostrophe and period for names
    // like "O'Brien" or "Mary-Jane". No digits — that was the break: a name is
    // not a phone number.
    @NotBlank(message = "First name is required")
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "First name must contain only letters")
    private String firstname;

    @NotBlank(message = "Last name is required")
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Last name must contain only letters")
    private String second_name;

    @ValidEmail
    @NotBlank(message = "Email is required")
    private String email;

    // Nigerian mobile, 11 digits (see PhoneNumbers). Strip spaces/dashes
    // client-side before sending.
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = PhoneNumbers.NIGERIAN_MOBILE_REGEX, message = PhoneNumbers.NIGERIAN_MOBILE_MESSAGE)
    private String phone_number;
    
    @NotBlank(message = "Password is required")
    private String password;

    // Optional referral code — attributes this user to a referrer at registration.
    private String referralCode;

    /**
     * The terms version the user was shown and agreed to.
     *
     * <p>Deliberately not @NotBlank. Refusing an account because an older app
     * build omits the field would lock real customers out over a bookkeeping
     * detail; instead the acceptance is recorded against whatever version was
     * shown, defaulting to the one currently in force, and the client is asked
     * to confirm on next launch via the flag on /me. Consent is captured either
     * way -- what changes is only whether we can prove the exact text.
     */
    private String acceptedTermsVersion;

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

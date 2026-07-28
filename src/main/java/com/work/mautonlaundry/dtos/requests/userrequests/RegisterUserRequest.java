package com.work.mautonlaundry.dtos.requests.userrequests;

import com.work.mautonlaundry.util.ValidEmail;
import jakarta.validation.constraints.NotBlank;
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

    // Optional leading +, then 7–15 digits (E.164 range). Rejects "V" and other
    // non-numeric input. Strip spaces/dashes client-side before sending.
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Enter a valid phone number")
    private String phone_number;
    
    @NotBlank(message = "Password is required")
    private String password;

    // Optional referral code — attributes this user to a referrer at registration.
    private String referralCode;

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

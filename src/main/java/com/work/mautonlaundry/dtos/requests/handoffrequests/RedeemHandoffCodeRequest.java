package com.work.mautonlaundry.dtos.requests.handoffrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RedeemHandoffCodeRequest {

    @NotBlank(message = "bookingId is required")
    private String bookingId;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "\\d{4,8}", message = "code must be 4–8 digits")
    private String code;
}

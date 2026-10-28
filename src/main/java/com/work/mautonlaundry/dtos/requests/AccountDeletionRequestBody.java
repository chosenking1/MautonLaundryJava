package com.work.mautonlaundry.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Email is used only by the public web page; the in-app request takes the signed-in user. */
@Data
public class AccountDeletionRequestBody {
    private String email;

    @NotBlank(message = "Enter your password to confirm")
    private String password;
}

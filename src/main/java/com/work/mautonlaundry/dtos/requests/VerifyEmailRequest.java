package com.work.mautonlaundry.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {
    @NotBlank
    private String token;
}
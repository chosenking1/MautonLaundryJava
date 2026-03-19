package com.work.mautonlaundry.dtos.requests.agentapplications;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectAgentApplicationRequest {
    @NotBlank(message = "Rejection reason is required")
    private String reason;
}

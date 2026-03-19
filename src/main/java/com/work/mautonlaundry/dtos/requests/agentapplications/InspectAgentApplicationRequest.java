package com.work.mautonlaundry.dtos.requests.agentapplications;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InspectAgentApplicationRequest {
    @NotNull(message = "Inspection result is required")
    private Boolean passed;
    private String notes;
    private List<String> failedAddressIds;
    private String rejectionReason;
}

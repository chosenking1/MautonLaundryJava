package com.work.mautonlaundry.dtos.responses.agentapplications;

import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import com.work.mautonlaundry.data.model.enums.InspectionStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentApplicationResponse {
    private Long id;
    private String userId;
    private String userEmail;
    private AgentApplicationType type;
    private AgentApplicationStatus status;
    private InspectionStatus inspectionStatus;
    private String rejectionReason;
    private String inspectionNotes;
    private List<String> addressIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

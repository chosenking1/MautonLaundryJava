package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogResponse {
    private String id;
    private String action;
    private String resource;
    private String resourceId;
    private LocalDateTime timestamp;
    private String details;
    private String userEmail;
}

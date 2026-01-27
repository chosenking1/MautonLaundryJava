package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String userEmail;
    
    @Column(nullable = false)
    private String action;
    
    @Column(nullable = false)
    private String resource;
    
    private String resourceId;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    private String ipAddress;
    
    private String userAgent;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
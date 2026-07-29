package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * State before and after the change (Permission Architecture V2, spec §8.5).
     *
     * <p>The spec creates a separate audit_log table for these; they are added to
     * the audit_logs this project already has instead, since two parallel audit
     * tables would be worse than either.
     *
     * <p>Both nullable: existing rows have no before/after, and most audited
     * actions are not state transitions. An access change is -- "who could do
     * what, before and after" is the only question an access audit exists to
     * answer.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;
    
    private String ipAddress;
    
    private String userAgent;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
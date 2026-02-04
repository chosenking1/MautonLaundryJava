package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
    List<AuditLog> findByResourceAndResourceIdOrderByTimestampDesc(String resource, String resourceId);
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    
    // Analytics methods
    Long countByActionAndTimestampAfter(String action, LocalDateTime timestamp);
    List<AuditLog> findTop10ByOrderByTimestampDesc();
    
    // Pagination support
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
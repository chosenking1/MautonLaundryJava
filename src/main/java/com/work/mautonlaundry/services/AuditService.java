package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.AuditLog;
import com.work.mautonlaundry.data.repository.AuditLogRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Async
    public void logAction(String action, String resource, String resourceId, String details) {
        try {
            AppUser currentUser = SecurityUtil.getCurrentUser().orElse(null);
            if (currentUser == null) return;
            
            HttpServletRequest request = getCurrentRequest();
            
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(currentUser.getId());
            auditLog.setUserEmail(currentUser.getEmail());
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setResourceId(resourceId);
            auditLog.setDetails(details);
            
            if (request != null) {
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't fail the main operation
        }
    }
    
    @Async
    public void logAction(String action, String resource, String resourceId) {
        logAction(action, resource, resourceId, null);
    }
    
    @Async
    public void logAction(String action, String resource) {
        logAction(action, resource, null, null);
    }
    
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        }
        return xForwardedForHeader.split(",")[0];
    }
}
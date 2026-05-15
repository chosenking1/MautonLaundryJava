package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.AuditLog;
import com.work.mautonlaundry.data.repository.AuditLogRepository;
import com.work.mautonlaundry.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void logAction(String action, String resource, String resourceId, String details) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElse(null);
        if (currentUser == null) {
            log.debug("Skipping audit log for {} {} - no authenticated user", action, resource);
            return;
        }

        String ipAddress = null;
        String userAgent = null;
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            ipAddress = getClientIpAddress(request);
            userAgent = request.getHeader("User-Agent");
        }

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(currentUser.getId());
            auditLog.setUserEmail(currentUser.getEmail());
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setResourceId(resourceId);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist audit log for action {} resource {}: {}", action, resource, e.getMessage());
        }
    }

    public void logAction(String action, String resource, String resourceId) {
        logAction(action, resource, resourceId, null);
    }

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

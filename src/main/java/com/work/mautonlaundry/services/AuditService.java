package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.AuditLog;

import java.util.Map;
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

    /**
     * Audits a state transition, recording what it was and what it became
     * (Permission Architecture V2, spec §8.5: "Every permission grant, revoke,
     * module assignment, role change, scope change ... This is not optional").
     *
     * <p>For access changes the before/after is the whole point: "who could do
     * what, and when did that change" is the only question an access audit
     * exists to answer, and an action name alone cannot answer it.
     *
     * @param oldValue state before, or null when the thing did not exist
     * @param newValue state after, or null when it was removed
     */
    public void logChange(String action, String resource, String resourceId,
                          Map<String, Object> oldValue, Map<String, Object> newValue) {
        logAction(action, resource, resourceId, null, oldValue, newValue);
    }

    public void logAction(String action, String resource, String resourceId, String details) {
        logAction(action, resource, resourceId, details, null, null);
    }

    private void logAction(String action, String resource, String resourceId, String details,
                           Map<String, Object> oldValue, Map<String, Object> newValue) {
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
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);
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

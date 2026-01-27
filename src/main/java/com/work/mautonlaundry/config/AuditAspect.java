package com.work.mautonlaundry.config;

import com.work.mautonlaundry.security.util.Auditable;
import com.work.mautonlaundry.services.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Aspect
@Component
public class AuditAspect {
    
    @Autowired
    private AuditService auditService;
    
    @AfterReturning("@annotation(auditable)")
    public void auditMethod(JoinPoint joinPoint, Auditable auditable) {
        try {
            String resourceId = null;
            
            if (!auditable.resourceIdParam().isEmpty()) {
                Object[] args = joinPoint.getArgs();
                for (Object arg : args) {
                    if (arg != null) {
                        try {
                            Field field = arg.getClass().getDeclaredField(auditable.resourceIdParam());
                            field.setAccessible(true);
                            resourceId = String.valueOf(field.get(arg));
                            break;
                        } catch (Exception e) {
                            if (auditable.resourceIdParam().equals("id") && arg instanceof String) {
                                resourceId = (String) arg;
                                break;
                            }
                        }
                    }
                }
            }
            
            auditService.logAction(auditable.action(), auditable.resource(), resourceId);
        } catch (Exception e) {
            // Log error but don't fail the main operation
        }
    }
}
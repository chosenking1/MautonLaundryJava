package com.work.mautonlaundry.security.filter;

import com.work.mautonlaundry.services.DynamicPermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DynamicPermissionFilter extends OncePerRequestFilter {
    
    private final DynamicPermissionService dynamicPermissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Skip filter for unauthenticated requests or public endpoints
        if (authentication == null || !authentication.isAuthenticated() || 
            request.getRequestURI().startsWith("/api/auth/") || 
            request.getRequestURI().equals("/register") ||
            request.getRequestURI().startsWith("/api/v1/services") ||
            request.getRequestURI().startsWith("/api/v1/payments/webhooks") ||
            request.getRequestURI().startsWith("/api/v1/payments/callback") ||
            request.getRequestURI().startsWith("/api/v1/pricing") ||
            request.getRequestURI().startsWith("/api/v1/agent-applications") ||
            request.getRequestURI().startsWith("/api/v1/agents") ||
            request.getRequestURI().startsWith("/agents") ||
            request.getRequestURI().startsWith("/api/v1/deliveries") ||
            request.getRequestURI().startsWith("/deliveries") ||
            request.getRequestURI().startsWith("/api/v1/addresses") ||
            request.getRequestURI().startsWith("/api/v1/users") ||
            request.getRequestURI().startsWith("/api/v1/admin/") ||
            request.getRequestURI().startsWith("/api/v1/categories") ||
            request.getRequestURI().startsWith("/swagger-ui") ||
            request.getRequestURI().startsWith("/v3/api-docs") ||
            request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String endpoint = request.getRequestURI();
        String method = request.getMethod();
        
        // Check dynamic permission
        if (!dynamicPermissionService.hasPermission(endpoint, method)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Access denied - Insufficient permissions\"}");
            response.setContentType("application/json");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}

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
            request.getRequestURI().equals("/register")) {
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
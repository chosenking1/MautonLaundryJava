package com.work.mautonlaundry.security.filter;

import com.work.mautonlaundry.security.service.JwtTokenProvider;
import com.work.mautonlaundry.services.AccountDeletionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Execute Before Executing Spring Security Filters
// Validate the JWT Token and Provides user details to Spring Security for Authentication
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private JwtTokenProvider jwtTokenProvider;

    private UserDetailsService userDetailsService;

    private AccountDeletionService accountDeletionService;

    public JwtAuthenticationFilter(@Lazy JwtTokenProvider jwtTokenProvider,
                                   @Lazy UserDetailsService userDetailsService,
                                   @Lazy AccountDeletionService accountDeletionService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.accountDeletionService = accountDeletionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String token = getTokenFromRequest(request);

        if(StringUtils.hasText(token) && !token.trim().isEmpty() && jwtTokenProvider.validateToken(token)){
            String username = jwtTokenProvider.getUsername(token);

            // Tokens last 7 days and cannot be revoked, so the lock lives here:
            // an account awaiting deletion is treated as signed out on every
            // device until its owner logs in again (which cancels the request).
            if (accountDeletionService.isPendingDeletion(username)) {
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException e) {
                // A deleted account's email is replaced, so its old tokens name
                // nobody. Unauthenticated, not a 500.
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request){
        String bearerToken = request.getHeader("Authorization");

        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7, bearerToken.length());
        }

        return null;
    }
}

package com.work.mautonlaundry.security.util;

import com.work.mautonlaundry.data.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtil {

    public static Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        return Optional.ofNullable((User) authentication.getPrincipal());
    }
}


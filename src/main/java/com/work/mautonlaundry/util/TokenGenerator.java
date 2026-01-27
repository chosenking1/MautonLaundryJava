package com.work.mautonlaundry.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenGenerator {
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    public String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
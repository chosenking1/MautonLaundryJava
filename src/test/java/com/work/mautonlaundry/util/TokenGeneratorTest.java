package com.work.mautonlaundry.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TokenGeneratorTest {

    @InjectMocks
    private TokenGenerator tokenGenerator;

    @Test
    void generateSecureToken_ReturnsNonNullToken() {
        String token = tokenGenerator.generateSecureToken();
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateSecureToken_ReturnsUniqueTokens() {
        String token1 = tokenGenerator.generateSecureToken();
        String token2 = tokenGenerator.generateSecureToken();
        
        assertNotEquals(token1, token2);
    }

    @Test
    void generateSecureToken_ReturnsBase64UrlSafeToken() {
        String token = tokenGenerator.generateSecureToken();
        
        // Base64 URL-safe tokens should not contain + / = characters
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
        assertFalse(token.contains("="));
    }

    @Test
    void generateSecureToken_MinimumLength() {
        String token = tokenGenerator.generateSecureToken();
        
        // 32 bytes encoded in Base64 should be at least 43 characters
        assertTrue(token.length() >= 43);
    }

    @Test
    void generateSecureToken_MultipleCallsReturnDifferentTokens() {
        String[] tokens = new String[10];
        for (int i = 0; i < 10; i++) {
            tokens[i] = tokenGenerator.generateSecureToken();
        }
        
        // All tokens should be unique
        for (int i = 0; i < tokens.length; i++) {
            for (int j = i + 1; j < tokens.length; j++) {
                assertNotEquals(tokens[i], tokens[j]);
            }
        }
    }

    @Test
    void generateSecureToken_OnlyValidCharacters() {
        String token = tokenGenerator.generateSecureToken();
        
        // Should only contain Base64 URL-safe characters: A-Z, a-z, 0-9, -, _
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }
}
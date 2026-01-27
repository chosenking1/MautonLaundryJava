package com.work.mautonlaundry.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    void sendVerificationEmail_Success() {
        assertDoesNotThrow(() -> 
            emailService.sendVerificationEmail("test@example.com", "token123"));
    }

    @Test
    void sendPasswordResetEmail_Success() {
        assertDoesNotThrow(() -> 
            emailService.sendPasswordResetEmail("test@example.com", "reset-token"));
    }

    @Test
    void sendBookingNotification_Success() {
        assertDoesNotThrow(() -> 
            emailService.sendBookingNotification("test@example.com", "booking-123", "CONFIRMED"));
    }

    @Test
    void sendVerificationEmail_NullEmail() {
        assertDoesNotThrow(() -> 
            emailService.sendVerificationEmail(null, "token123"));
    }

    @Test
    void sendVerificationEmail_EmptyEmail() {
        assertDoesNotThrow(() -> 
            emailService.sendVerificationEmail("", "token123"));
    }

    @Test
    void sendVerificationEmail_NullToken() {
        assertDoesNotThrow(() -> 
            emailService.sendVerificationEmail("test@example.com", null));
    }

    @Test
    void sendPasswordResetEmail_InvalidEmail() {
        assertDoesNotThrow(() -> 
            emailService.sendPasswordResetEmail("invalid-email", "reset-token"));
    }

    @Test
    void sendBookingNotification_NullParameters() {
        assertDoesNotThrow(() -> 
            emailService.sendBookingNotification(null, null, null));
    }

    @Test
    void sendBookingNotification_EmptyParameters() {
        assertDoesNotThrow(() -> 
            emailService.sendBookingNotification("", "", ""));
    }
}
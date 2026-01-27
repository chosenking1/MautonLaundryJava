package com.work.mautonlaundry.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public void sendVerificationEmail(String email, String token) {
        // TODO: Implement actual email sending
        log.info("Sending verification email to: {} with token: {}", email, token);
    }

    @Override
    public void sendPasswordResetEmail(String email, String token) {
        // TODO: Implement actual email sending
        log.info("Sending password reset email to: {} with token: {}", email, token);
    }

    @Override
    public void sendBookingNotification(String email, String bookingId, String status) {
        // TODO: Implement actual email sending
        log.info("Sending booking notification to: {} for booking: {} with status: {}", email, bookingId, status);
    }
}
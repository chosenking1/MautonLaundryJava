package com.work.mautonlaundry.services;

public interface EmailService {
    void sendVerificationEmail(String email, String token);
    void sendPasswordResetEmail(String email, String token);
    void sendBookingNotification(String email, String bookingId, String status);
}
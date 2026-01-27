package com.work.mautonlaundry.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private EmailService emailService;

    public void notifyBookingStatusChange(String userEmail, String bookingId, String oldStatus, String newStatus) {
        String message = String.format("Booking %s status changed from %s to %s", bookingId, oldStatus, newStatus);
        emailService.sendBookingNotification(userEmail, bookingId, message);
    }

    public void notifyBookingCreated(String userEmail, String bookingId) {
        emailService.sendBookingNotification(userEmail, bookingId, "Booking created successfully");
    }

    public void notifyBookingCompleted(String userEmail, String bookingId) {
        emailService.sendBookingNotification(userEmail, bookingId, "Booking completed");
    }
}
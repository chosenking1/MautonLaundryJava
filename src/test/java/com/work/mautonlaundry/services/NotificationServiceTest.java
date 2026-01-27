package com.work.mautonlaundry.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void notifyBookingStatusChange_Success() {
        notificationService.notifyBookingStatusChange("test@example.com", "booking-123", "PENDING", "CONFIRMED");

        verify(emailService).sendBookingNotification(
            eq("test@example.com"), 
            eq("booking-123"), 
            contains("PENDING")
        );
    }

    @Test
    void notifyBookingCreated_Success() {
        notificationService.notifyBookingCreated("test@example.com", "booking-123");

        verify(emailService).sendBookingNotification(
            eq("test@example.com"), 
            eq("booking-123"), 
            eq("Booking created successfully")
        );
    }

    @Test
    void notifyBookingCompleted_Success() {
        notificationService.notifyBookingCompleted("test@example.com", "booking-123");

        verify(emailService).sendBookingNotification(
            eq("test@example.com"), 
            eq("booking-123"), 
            eq("Booking completed")
        );
    }

    @Test
    void notifyBookingStatusChange_NullEmail() {
        notificationService.notifyBookingStatusChange(null, "booking-123", "PENDING", "CONFIRMED");

        verify(emailService).sendBookingNotification(
            isNull(), 
            eq("booking-123"), 
            anyString()
        );
    }

    @Test
    void notifyBookingStatusChange_NullBookingId() {
        notificationService.notifyBookingStatusChange("test@example.com", null, "PENDING", "CONFIRMED");

        verify(emailService).sendBookingNotification(
            eq("test@example.com"), 
            isNull(), 
            anyString()
        );
    }

    @Test
    void notifyBookingStatusChange_NullStatuses() {
        notificationService.notifyBookingStatusChange("test@example.com", "booking-123", null, null);

        verify(emailService).sendBookingNotification(
            eq("test@example.com"), 
            eq("booking-123"), 
            contains("null")
        );
    }

    @Test
    void notifyBookingCreated_EmptyParameters() {
        notificationService.notifyBookingCreated("", "");

        verify(emailService).sendBookingNotification(
            eq(""), 
            eq(""), 
            eq("Booking created successfully")
        );
    }

    @Test
    void notifyBookingCompleted_NullParameters() {
        notificationService.notifyBookingCompleted(null, null);

        verify(emailService).sendBookingNotification(
            isNull(), 
            isNull(), 
            eq("Booking completed")
        );
    }
}
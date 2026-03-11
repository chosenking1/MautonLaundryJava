package com.work.mautonlaundry.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class NotificationService {

    @Autowired
    private EmailService emailService;
    private final ExecutorService notificationExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdownExecutor() {
        notificationExecutor.shutdown();
    }

    public void notifyBookingStatusChange(String userEmail, String bookingId, String oldStatus, String newStatus) {
        String message = String.format("Booking %s status changed from %s to %s", bookingId, oldStatus, newStatus);
        dispatchNotification(userEmail, bookingId, message);
    }

    public void notifyUserBookingStatusChange(String userEmail, String bookingId, String oldStatus, String newStatus) {
        String statusDescription = formatStatusDescription(newStatus);
        String message = String.format("Your booking #%s status has been updated to: %s", bookingId, statusDescription);
        dispatchNotification(userEmail, bookingId, message);
    }

    public void notifyUserLaundrymanAssigned(String userEmail, String bookingId, String laundrymanName) {
        String message = String.format(
                "Great news! A laundry agent has been assigned to your booking #%s. They will soon pick up your items.",
                bookingId
        );
        dispatchNotification(userEmail, bookingId, message);
    }

    public void notifyBookingCreated(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Booking created successfully");
    }

    public void notifyBookingCompleted(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Booking completed");
    }

    public void notifyDeliveryOffer(String deliveryAgentEmail, String bookingId, String phase, double distanceKm) {
        String message = String.format(
                "New %s assignment offer for booking %s. You are %.2f km away. Accept in app.",
                phase, bookingId, distanceKm
        );
        dispatchNotification(deliveryAgentEmail, bookingId, message);
    }

    public void notifyLaundrymanAssigned(String laundrymanEmail, String bookingId, long activeLoad) {
        String message = String.format(
                "New washing assignment for booking %s. Current active workload: %d",
                bookingId, activeLoad
        );
        dispatchNotification(laundrymanEmail, bookingId, message);
    }

    public void notifyEarlyDeliveryOption(String userEmail, String bookingId, String suggestedDate) {
        String message = String.format(
                "Booking %s is ready early. Would you like delivery on %s?",
                bookingId,
                suggestedDate
        );
        dispatchNotification(userEmail, bookingId, message);
    }

    private void dispatchNotification(String email, String bookingId, String message) {
        if (email == null || email.isBlank()) {
            log.warn("Cannot dispatch notification: email is null or blank for booking {}", bookingId);
            return;
        }
        
        notificationExecutor.submit(() -> {
            try {
                log.info("Sending notification email to: {} for booking: {}", email, bookingId);
                emailService.sendBookingNotification(email, bookingId, message);
                log.info("Notification sent successfully to: {}", email);
            } catch (Exception ex) {
                log.error("Failed to dispatch notification for booking {}. Error: {}", bookingId, ex.getMessage(), ex);
            }
        });
    }

    private String formatStatusDescription(String status) {
        return switch (status) {
            case "CREATED" -> "Order Placed";
            case "CONFIRMED" -> "Order Confirmed";
            case "PENDING_ASSIGNMENT" -> "Finding Laundry Agent";
            case "PENDING_LAUNDRYMAN_ACCEPTANCE" -> "Awaiting Agent Acceptance";
            case "LAUNDRYMAN_ACCEPTED" -> "Agent Accepted";
            case "ENROUTE_FOR_COLLECTION" -> "Agent En Route to Pickup";
            case "COLLECTED_FROM_CUSTOMER" -> "Items Collected";
            case "RECEIVED_BY_LAUNDRY" -> "At Laundry";
            case "READY_FOR_PICKUP" -> "Ready for Pickup";
            case "ENROUTE_TO_CUSTOMER" -> "Out for Delivery";
            case "READY_FOR_DELIVERY" -> "Ready for Delivery";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery";
            case "DELIVERED" -> "Delivered";
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            default -> status.replace("_", " ");
        };
    }
}

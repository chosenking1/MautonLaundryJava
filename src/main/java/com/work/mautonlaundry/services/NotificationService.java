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

    public void notifyLaundryOffer(String laundrymanEmail, String bookingId) {
        String message = String.format(
                "New laundry offer for booking %s. Please accept or reject in app.",
                bookingId
        );
        dispatchNotification(laundrymanEmail, bookingId, message);
    }

    public void notifyDriverAssigned(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "A driver has been assigned to your booking");
    }

    public void notifyDriverArriving(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Your driver is arriving soon");
    }

    public void notifyLaundryReady(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Your laundry is ready for delivery");
    }

    public void notifyOutForDelivery(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Your laundry is out for delivery");
    }

    public void notifyDelivered(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, "Your laundry has been delivered");
    }

    public void notifyEarlyDeliveryOption(String userEmail, String bookingId, String scheduledDate, String nextDayDate) {
        String message = String.format(
                "Booking %s is ready early. Would you prefer delivery on your scheduled date (%s) or on the next day (%s)?",
                bookingId,
                scheduledDate,
                nextDayDate
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
            case "LAUNDRY_ASSIGNMENT_PENDING" -> "Finding Laundry";
            case "LAUNDRY_ACCEPTED" -> "Laundry Accepted";
            case "PICKUP_DISPATCH_PENDING" -> "Dispatching Pickup";
            case "PICKUP_AGENT_ASSIGNED" -> "Pickup Agent Assigned";
            case "PICKED_UP" -> "Picked Up";
            case "AT_LAUNDRY" -> "At Laundry";
            case "WASHING" -> "Washing";
            case "READY_FOR_DELIVERY" -> "Ready for Delivery";
            case "DELIVERY_DISPATCH_PENDING" -> "Dispatching Delivery";
            case "DELIVERY_AGENT_ASSIGNED" -> "Delivery Agent Assigned";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery";
            case "DELIVERED" -> "Delivered";
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            default -> status.replace("_", " ");
        };
    }
}

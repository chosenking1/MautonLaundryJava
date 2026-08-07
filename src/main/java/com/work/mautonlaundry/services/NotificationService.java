package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.repository.BookingRepository;
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
    @Autowired
    private BookingRepository bookingRepository;
    private final ExecutorService notificationExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdownExecutor() {
        notificationExecutor.shutdown();
    }

    public void notifyBookingStatusChange(String userEmail, String bookingId, String oldStatus, String newStatus) {
        // Was "status changed from X to Y" with the raw enum names, which is how
        // "DELIVERED_TO_LAUNDRY" reached customers. Nobody outside this codebase
        // needs the previous state either -- they need to know what just happened.
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus(newStatus));
    }

    public void notifyUserBookingStatusChange(String userEmail, String bookingId, String oldStatus, String newStatus) {
        // "Your booking #<uuid> status has been updated to: At Laundry" told the
        // customer a label and an identifier. This tells them what happened.
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus(newStatus));
    }

    public void notifyUserLaundrymanAssigned(String userEmail, String bookingId, String laundrymanName) {
        // Was "your booking #<uuid>" -- the customer has never seen that value
        // anywhere and cannot match it to the order in their app.
        dispatchNotification(userEmail, bookingId,
                "A laundry partner has accepted your order. We'll arrange collection next.");
    }

    /**
     * @param pickupWindow the window the customer chose, or null for "collect
     *                     now". A held booking must not be told we are already
     *                     looking for a rider -- it is deliberately not.
     */
    public void notifyBookingCreated(String userEmail, String bookingId, String pickupWindow) {
        dispatchNotification(userEmail, bookingId,
                pickupWindow == null || pickupWindow.isBlank()
                        ? CustomerStatusText.forStatus("CREATED")
                        : CustomerStatusText.forScheduledPickup(pickupWindow));
    }

    /** Staff have replied to a complaint. */
    public void notifySupportReply(String userEmail, String ticketId, String subject) {
        dispatchNotification(userEmail, ticketId,
                "We've replied to your message about \"" + subject + "\". Open Help & Support to read it.");
    }


    public void notifyDeliveryOffer(String deliveryAgentEmail, String bookingId, String phase, double distanceKm) {
        dispatchNotification(deliveryAgentEmail, bookingId,
                AgentNotificationText.deliveryOffer(phase, referenceFor(bookingId), distanceKm));
    }


    public void notifyLaundryOffer(String laundrymanEmail, String bookingId) {
        dispatchNotification(laundrymanEmail, bookingId,
                AgentNotificationText.laundryOffer(referenceFor(bookingId)));
    }

    public void notifyDriverAssigned(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus("PICKUP_AGENT_ASSIGNED"));
    }


    public void notifyLaundryReady(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus("READY_FOR_DELIVERY"));
    }

    public void notifyOutForDelivery(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus("OUT_FOR_DELIVERY"));
    }

    public void notifyDelivered(String userEmail, String bookingId) {
        dispatchNotification(userEmail, bookingId, CustomerStatusText.forStatus("DELIVERED"));
    }

    public void notifyDeliveryProgress(String userEmail, String bookingId, String message) {
        dispatchNotification(userEmail, bookingId, message);
    }

    public void notifyLaundryProgress(String laundryEmail, String bookingId, String message) {
        dispatchNotification(laundryEmail, bookingId, message);
    }

    public void notifyEarlyDeliveryOption(String userEmail, String bookingId, String scheduledDate, String nextDayDate) {
        // The order number is printed in the email body already; naming it in
        // the sentence too just crowds out the question being asked.
        String message = String.format(
                "Your laundry is ready ahead of schedule. Would you prefer it delivered on your "
                        + "original date (%s), or a day earlier (%s)?",
                scheduledDate,
                nextDayDate
        );
        dispatchNotification(userEmail, bookingId, message);
    }

    public void notifyAgentApplicationSubmitted(String userEmail, String roleName, int locationsCount, String adminTeamEmail) {
        notificationExecutor.submit(() -> {
            try {
                emailService.sendAgentApplicationSubmitted(userEmail, roleName, locationsCount, adminTeamEmail);
            } catch (Exception ex) {
                log.error("Failed to send application submitted email to {}. Error: {}", userEmail, ex.getMessage(), ex);
            }
        });
    }

    public void notifyAdminNewAgentApplication(String adminEmail, String applicantEmail, String roleName, int locationsCount) {
        notificationExecutor.submit(() -> {
            try {
                emailService.sendAdminAgentApplicationNotification(adminEmail, applicantEmail, roleName, locationsCount);
            } catch (Exception ex) {
                log.error("Failed to send admin application notification. Error: {}", ex.getMessage(), ex);
            }
        });
    }

    public void notifyAgentApplicationApproved(String userEmail, String roleName) {
        notificationExecutor.submit(() -> {
            try {
                emailService.sendAgentApplicationApproved(userEmail, roleName);
            } catch (Exception ex) {
                log.error("Failed to send application approved email to {}. Error: {}", userEmail, ex.getMessage(), ex);
            }
        });
    }

    public void notifyAgentApplicationRejected(String userEmail, String roleName, String reason, String adminTeamEmail) {
        notificationExecutor.submit(() -> {
            try {
                emailService.sendAgentApplicationRejected(userEmail, roleName, reason, adminTeamEmail);
            } catch (Exception ex) {
                log.error("Failed to send application rejected email to {}. Error: {}", userEmail, ex.getMessage(), ex);
            }
        });
    }

    public void notifyAgentDeactivated(String userEmail, String roleName, String adminTeamEmail, String reason) {
        notificationExecutor.submit(() -> {
            try {
                emailService.sendAgentDeactivated(userEmail, roleName, adminTeamEmail, reason);
            } catch (Exception ex) {
                log.error("Failed to send deactivation email to {}. Error: {}", userEmail, ex.getMessage(), ex);
            }
        });
    }

    /**
     * The order's tracking number -- the reference the customer and the apps
     * actually show.
     *
     * <p>Emails used to derive a reference by truncating the booking's UUID,
     * which produced a code that appears nowhere else in the product: support
     * could not look it up and the customer could not match it to anything on
     * screen. Null when it cannot be resolved, and callers omit the reference
     * entirely rather than falling back to an internal id.
     */
    private String referenceFor(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return null;
        }
        try {
            return bookingRepository.findById(bookingId)
                    .map(Booking::getTrackingNumber)
                    .filter(t -> t != null && !t.isBlank())
                    .orElse(null);
        } catch (Exception e) {
            // A notification is worth sending without its reference; it is not
            // worth failing over one.
            log.warn("Could not resolve tracking number for booking {}: {}", bookingId, e.getMessage());
            return null;
        }
    }

    private void dispatchNotification(String email, String bookingId, String message) {
        if (email == null || email.isBlank()) {
            log.warn("Cannot dispatch notification: email is null or blank for booking {}", bookingId);
            return;
        }

        String reference = referenceFor(bookingId);
        notificationExecutor.submit(() -> {
            try {
                log.info("Sending notification email to: {} for booking: {}", email, bookingId);
                emailService.sendBookingNotification(email, reference, message);
                log.info("Notification sent successfully to: {}", email);
            } catch (Exception ex) {
                log.error("Failed to dispatch notification for booking {}. Error: {}", bookingId, ex.getMessage(), ex);
            }
        });
    }

}

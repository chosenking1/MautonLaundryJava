package com.work.mautonlaundry.services;

public interface EmailService {
    void sendVerificationEmail(String email, String token);

    /**
     * Sent once, after an address is actually confirmed. Separate from the
     * verification mail on purpose: that one has a job to do and anything else in
     * it competes with the button.
     *
     * @param firstName may be null or blank; the greeting degrades to no name
     *                  rather than "Hi null".
     */
    void sendWelcomeEmail(String email, String firstName);

    /** Account deletion requested: when it happens and how to stop it. */
    void sendAccountDeletionScheduled(String email, String firstName, String deletionDate);

    /** The customer logged back in during the grace period. */
    void sendAccountDeletionCancelled(String email, String firstName);

    /** Sent to the original address once the account has been anonymised. */
    void sendAccountDeleted(String email, String firstName);

    void sendPasswordResetEmail(String email, String token);
    /**
     * @param reference the order's tracking number as the customer sees it in
     *                  the app, or null to omit it. Never an internal id.
     * @param message   a sentence already written for the reader, not a status
     *                  name. Pass raw enum values here and they read them --
     *                  see {@link CustomerStatusText} and
     *                  {@link AgentNotificationText}, which exist to convert.
     */
    void sendBookingNotification(String email, String reference, String message);
    void sendAgentApplicationSubmitted(String email, String roleName, int locationsCount, String adminTeamEmail);
    void sendAdminAgentApplicationNotification(String adminEmail, String applicantEmail, String roleName, int locationsCount);
    void sendAgentApplicationApproved(String email, String roleName);
    void sendAgentApplicationRejected(String email, String roleName, String reason, String adminTeamEmail);
    void sendAgentDeactivated(String email, String roleName, String adminTeamEmail, String reason);
    void sendDiscountApprovedEmail(String email, String discountName);
}

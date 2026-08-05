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

    void sendPasswordResetEmail(String email, String token);
    /**
     * @param message a sentence already written for the customer, not a status
     *                name. Pass raw enum values here and the customer reads them
     *                -- see {@link CustomerStatusText}, which exists to convert.
     */
    void sendBookingNotification(String email, String bookingId, String message);
    void sendAgentApplicationSubmitted(String email, String roleName, int locationsCount, String adminTeamEmail);
    void sendAdminAgentApplicationNotification(String adminEmail, String applicantEmail, String roleName, int locationsCount);
    void sendAgentApplicationApproved(String email, String roleName);
    void sendAgentApplicationRejected(String email, String roleName, String reason, String adminTeamEmail);
    void sendAgentDeactivated(String email, String roleName, String adminTeamEmail, String reason);
    void sendDiscountApprovedEmail(String email, String discountName);
}

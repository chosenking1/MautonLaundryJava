package com.work.mautonlaundry.services;

public interface EmailService {
    void sendVerificationEmail(String email, String token);
    void sendPasswordResetEmail(String email, String token);
    void sendBookingNotification(String email, String bookingId, String status);
    void sendAgentApplicationSubmitted(String email, String roleName, int locationsCount, String adminTeamEmail);
    void sendAdminAgentApplicationNotification(String adminEmail, String applicantEmail, String roleName, int locationsCount);
    void sendAgentApplicationApproved(String email, String roleName);
    void sendAgentApplicationRejected(String email, String roleName, String reason, String adminTeamEmail);
    void sendAgentDeactivated(String email, String roleName, String adminTeamEmail, String reason);
    void sendDiscountApprovedEmail(String email, String discountName);
}

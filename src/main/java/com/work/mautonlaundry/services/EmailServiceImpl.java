package com.work.mautonlaundry.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async
    public void sendVerificationEmail(String email, String token) {
        String verificationUrl = baseUrl + "/api/auth/verify-email?token=" + token;
        String subject = "Verify Your Email - " + fromName;
        String body = buildVerificationEmailBody(verificationUrl);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String email, String token) {
        String resetUrl = baseUrl + "/api/auth/reset-password?token=" + token;
        String subject = "Password Reset - " + fromName;
        String body = buildPasswordResetEmailBody(resetUrl);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendBookingNotification(String email, String bookingId, String status) {
        String subject = "Booking Update - " + fromName;
        String body = buildBookingNotificationBody(bookingId, status);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendAgentApplicationSubmitted(String email, String roleName, int locationsCount, String adminTeamEmail) {
        String subject = "Agent Application Received - " + fromName;
        String body = buildAgentApplicationSubmittedBody(roleName, locationsCount, adminTeamEmail);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendAdminAgentApplicationNotification(String adminEmail, String applicantEmail, String roleName, int locationsCount) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Admin team email not configured. Skipping admin notification.");
            return;
        }
        String subject = "New Agent Application - " + fromName;
        String body = buildAdminAgentApplicationNotificationBody(applicantEmail, roleName, locationsCount);
        sendEmail(adminEmail, subject, body);
    }

    @Override
    @Async
    public void sendAgentApplicationApproved(String email, String roleName) {
        String subject = "Application Approved - " + fromName;
        String body = buildAgentApplicationApprovedBody(roleName);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendAgentApplicationRejected(String email, String roleName, String reason, String adminTeamEmail) {
        String subject = "Application Update - " + fromName;
        String body = buildAgentApplicationRejectedBody(roleName, reason, adminTeamEmail);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendAgentDeactivated(String email, String roleName, String adminTeamEmail, String reason) {
        String subject = "Account Update - " + fromName;
        String body = buildAgentDeactivationBody(roleName, reason, adminTeamEmail);
        sendEmail(email, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            log.info("Attempting to send email to: {} with subject: {}", to, subject);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String from = fromAddress;
            if (fromName != null && !fromName.isEmpty()) {
                from = fromName + " <" + fromAddress + ">";
            }
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}. Error: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send email to: {}. Error: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildVerificationEmailBody(String verificationUrl) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Welcome to " + fromName + "!</h2>" +
                "<p>Thank you for registering. Please verify your email address by clicking the button below:</p>" +
                "<p><a href='" + verificationUrl + "' style='background-color: #4CAF50; color: white; padding: 12px 20px; text-decoration: none; border-radius: 4px; display: inline-block;'>Verify Email</a></p>" +
                "<p>Or copy and paste this link in your browser:</p>" +
                "<p>" + verificationUrl + "</p>" +
                "<p>This link will expire in 24 hours.</p>" +
                "<p>If you did not create an account, please ignore this email.</p>" +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildPasswordResetEmailBody(String resetUrl) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Password Reset Request</h2>" +
                "<p>We received a request to reset your password. Click the button below to reset it:</p>" +
                "<p><a href='" + resetUrl + "' style='background-color: #2196F3; color: white; padding: 12px 20px; text-decoration: none; border-radius: 4px; display: inline-block;'>Reset Password</a></p>" +
                "<p>Or copy and paste this link in your browser:</p>" +
                "<p>" + resetUrl + "</p>" +
                "<p>This link will expire in 1 hour.</p>" +
                "<p>If you did not request a password reset, please ignore this email.</p>" +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildBookingNotificationBody(String bookingId, String status) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Booking Update</h2>" +
                "<p>Your booking status has been updated.</p>" +
                "<p><strong>Booking ID:</strong> " + bookingId + "</p>" +
                "<p><strong>Status:</strong> " + status + "</p>" +
                "<p>You can track your order in your account dashboard.</p>" +
                "<p>Thank you for choosing " + fromName + "!</p>" +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildAgentApplicationSubmittedBody(String roleName, int locationsCount, String adminTeamEmail) {
        boolean isLaundry = "LAUNDRY_AGENT".equalsIgnoreCase(roleName);
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Application Received</h2>" +
                "<p>Thank you for applying to become a " + roleName.replace("_", " ").toLowerCase() + ".</p>" +
                "<p>We have recorded " + locationsCount + " location(s).</p>" +
                (isLaundry
                        ? "<p>Our team will conduct verification visits to the locations provided. Visits can happen on any day without prior notice.</p>" +
                          "<p>If any location fails inspection, the application will be declined.</p>"
                        : "<p>Our team will review your details and get back to you.</p>") +
                (adminTeamEmail == null || adminTeamEmail.isBlank()
                        ? ""
                        : "<p>If you need to update your information, contact us at " + adminTeamEmail + ".</p>") +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildAdminAgentApplicationNotificationBody(String applicantEmail, String roleName, int locationsCount) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>New Agent Application</h2>" +
                "<p>A new application has been submitted.</p>" +
                "<p><strong>Applicant:</strong> " + applicantEmail + "</p>" +
                "<p><strong>Role:</strong> " + roleName.replace("_", " ").toLowerCase() + "</p>" +
                "<p><strong>Locations:</strong> " + locationsCount + "</p>" +
                "<p>Please log in to the admin dashboard to review.</p>" +
                "</body>" +
                "</html>";
    }

    private String buildAgentApplicationApprovedBody(String roleName) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Application Approved</h2>" +
                "<p>Your application to become a " + roleName.replace("_", " ").toLowerCase() + " has been approved.</p>" +
                "<p>You can now log in to the Imototo Ops application to access agent functionality.</p>" +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildAgentApplicationRejectedBody(String roleName, String reason, String adminTeamEmail) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Application Update</h2>" +
                "<p>Your application to become a " + roleName.replace("_", " ").toLowerCase() + " was not approved.</p>" +
                "<p><strong>Reason:</strong> " + reason + "</p>" +
                (adminTeamEmail == null || adminTeamEmail.isBlank()
                        ? ""
                        : "<p>If you believe this is a mistake, contact us at " + adminTeamEmail + ".</p>") +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    private String buildAgentDeactivationBody(String roleName, String reason, String adminTeamEmail) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Account Update</h2>" +
                "<p>Your " + roleName.replace("_", " ").toLowerCase() + " access has been deactivated.</p>" +
                (reason == null || reason.isBlank() ? "" : "<p><strong>Reason:</strong> " + reason + "</p>") +
                (adminTeamEmail == null || adminTeamEmail.isBlank()
                        ? ""
                        : "<p>If you believe this is a mistake, please reach out to our admin team at " + adminTeamEmail + ".</p>") +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }

    @Override
    @Async
    public void sendDiscountApprovedEmail(String email, String discountName) {
        String subject = "Discount Approved - " + fromName;
        String body = buildDiscountApprovedBody(discountName);
        sendEmail(email, subject, body);
    }

    private String buildDiscountApprovedBody(String discountName) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2>Discount Approved!</h2>" +
                "<p>Great news! Your discount code has been verified and is now active.</p>" +
                "<p>You can now use your discount on your next order.</p>" +
                "<p>Thank you for choosing " + fromName + "!</p>" +
                "<p>Best regards,<br>The " + fromName + " Team</p>" +
                "</body>" +
                "</html>";
    }
}
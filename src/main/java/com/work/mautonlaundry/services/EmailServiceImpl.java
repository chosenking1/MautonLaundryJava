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
    public void sendWelcomeEmail(String email, String firstName) {
        String subject = "Welcome to " + fromName;
        sendEmail(email, subject, buildWelcomeEmailBody(firstName));
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String email, String token) {
        // Point at the public HTML reset page (which renders a working new-password form),
        // not the JSON API endpoint.
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String subject = "Password Reset - " + fromName;
        String body = buildPasswordResetEmailBody(resetUrl);
        sendEmail(email, subject, body);
    }

    @Override
    @Async
    public void sendBookingNotification(String email, String bookingId, String message) {
        // Every update used the same subject, so a customer's inbox filled with
        // identical lines and they had to open each one to learn anything.
        String subject = deriveSubject(message) + " - " + fromName;
        String body = buildBookingNotificationBody(bookingId, message);
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

    /**
     * The shared shell every customer-facing email sits in: navy header, white
     * card, quiet footer.
     *
     * <p>One shell rather than nine hand-rolled pages. The templates had drifted
     * into different fonts, colours and button styles -- the verification button
     * was green, which belongs to no part of the brand -- and each new email
     * copied whichever one the author happened to look at.
     *
     * <p>Inline styles on a table shell, deliberately: mail clients strip
     * stylesheets and do not implement flexbox, so surviving Gmail and Outlook
     * matters more here than writing modern CSS.
     */
    private String emailShell(String heading, String innerHtml) {
        return "<html><body style=\"margin:0;padding:0;background:#F5F7FA;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#F5F7FA;padding:24px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:520px;background:#FFFFFF;border-radius:12px;overflow:hidden;"
                + "font-family:'Segoe UI',Helvetica,Arial,sans-serif;\">"
                + "<tr><td style=\"background:#1A3A6B;padding:20px 24px;\">"
                + "<span style=\"color:#FFFFFF;font-size:18px;font-weight:700;letter-spacing:0.3px;\">"
                + fromName + "</span></td></tr>"
                + "<tr><td style=\"padding:28px 24px 8px 24px;\">"
                + (heading == null || heading.isBlank() ? ""
                        : "<h1 style=\"margin:0 0 12px 0;font-size:20px;font-weight:700;color:#1A1A2E;\">"
                                + heading + "</h1>")
                + innerHtml
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 24px 24px 24px;border-top:1px solid #E5E7EB;\">"
                + "<p style=\"margin:0;font-size:12px;color:#6B7280;\">"
                + fromName + ", Outsource the dirty work.</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Brand-navy action button. Table-based so Outlook renders the fill. */
    private String primaryButton(String url, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin:0 0 20px 0;\"><tr><td "
                + "style=\"background:#1A3A6B;border-radius:10px;\">"
                + "<a href=\"" + url + "\" style=\"display:inline-block;padding:13px 26px;"
                + "font-size:15px;font-weight:600;color:#FFFFFF;text-decoration:none;\">"
                + label + "</a></td></tr></table>";
    }

    /** Body copy. */
    private String para(String text) {
        return "<p style=\"margin:0 0 16px 0;font-size:15px;line-height:1.55;color:#1A1A2E;\">"
                + text + "</p>";
    }

    /** Secondary copy: expiry notes, "ignore this if..." lines. */
    private String note(String text) {
        return "<p style=\"margin:0 0 12px 0;font-size:13px;line-height:1.55;color:#6B7280;\">"
                + text + "</p>";
    }

    /**
     * A numbered step. A table rather than a list or flex row because Outlook
     * renders neither reliably, and the number must stay beside its text.
     */
    private String step(String number, String title, String detail) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" "
                + "style=\"margin:0 0 14px 0;\"><tr>"
                + "<td width=\"32\" valign=\"top\" style=\"padding:0 12px 0 0;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" width=\"26\" height=\"26\" "
                + "style=\"background:#EEF2F8;border-radius:13px;font-size:13px;font-weight:700;"
                + "color:#1A3A6B;\">" + number + "</td></tr></table></td>"
                + "<td valign=\"top\">"
                + "<p style=\"margin:0 0 2px 0;font-size:15px;font-weight:600;color:#1A1A2E;\">"
                + title + "</p>"
                + "<p style=\"margin:0;font-size:14px;line-height:1.5;color:#4B5563;\">"
                + detail + "</p></td></tr></table>";
    }

    /** A copyable URL, for clients that strip the button. */
    private String fallbackLink(String url) {
        return "<p style=\"margin:0 0 6px 0;font-size:13px;color:#6B7280;\">"
                + "If the button does not work, paste this into your browser:</p>"
                + "<p style=\"margin:0 0 20px 0;font-size:12px;line-height:1.5;"
                + "word-break:break-all;color:#1A3A6B;\">" + url + "</p>";
    }

    private String buildVerificationEmailBody(String verificationUrl) {
        return emailShell(
                "Confirm your email",
                para("Thanks for signing up. Confirm this address and your account is ready to use.")
                        + primaryButton(verificationUrl, "Confirm email")
                        + fallbackLink(verificationUrl)
                        + note("This link works for 24 hours.")
                        + note("If you did not create a " + fromName
                                + " account, you can ignore this email."));
    }

    /**
     * The first email that is not asking for anything.
     *
     * <p>Deliberately short and only about what exists today: book a collection,
     * follow it, ask for help. No feature is promised here that a new user cannot
     * find in the app on the day they read this.
     */
    private String buildWelcomeEmailBody(String firstName) {
        String greeting = firstName == null || firstName.isBlank()
                ? "You're all set."
                : "You're all set, " + firstName + ".";
        return emailShell(
                greeting,
                para("Your email is confirmed, so your " + fromName
                        + " account is ready. Here is how it works.")
                        + step("1", "Add your address",
                                "Drop a pin on the map so your rider arrives at the right gate, "
                                        + "not the right street.")
                        + step("2", "Book a collection",
                                "Choose what needs cleaning and when you want it picked up.")
                        + step("3", "Follow it in the app",
                                "You'll see each stage as it happens, from collection to the "
                                        + "moment it's back with you.")
                        + note("Something not right with an order? Use Help &amp; Support in the "
                                + "app and a person will read it.")
                        + note("Welcome aboard."));
    }

    private String buildPasswordResetEmailBody(String resetUrl) {
        return emailShell(
                "Reset your password",
                para("Use the button below to set a new password. If you did not ask for this, "
                        + "nothing has changed and you can ignore this email.")
                        + primaryButton(resetUrl, "Set a new password")
                        + fallbackLink(resetUrl)
                        + note("This link works for one hour, and once only.")
                        + note("If you did not request a reset, we recommend checking that no one "
                                + "else has access to your inbox."));
    }

    /**
     * A subject drawn from the message itself. The notification layer sends a
     * finished sentence, so its first clause is the news -- which beats every
     * update arriving as an identical "Booking Update" that has to be opened.
     */
    private String deriveSubject(String message) {
        if (message == null || message.isBlank()) return "Order update";
        String firstSentence = message.split("(?<=[.!?])\s", 2)[0].trim();
        if (firstSentence.endsWith(".")) {
            firstSentence = firstSentence.substring(0, firstSentence.length() - 1);
        }
        return firstSentence.length() > 60 ? "Order update" : firstSentence;
    }

    private String buildBookingNotificationBody(String bookingId, String message) {
        String reference = bookingId == null || bookingId.length() < 8
                ? bookingId
                : bookingId.substring(0, 8).toUpperCase();
        return emailShell(
                null,
                para(message)
                        + "<p style=\"margin:0 0 4px 0;font-size:13px;color:#6B7280;\">Order reference</p>"
                        + "<p style=\"margin:0 0 20px 0;font-size:15px;font-weight:600;color:#1A3A6B;\">"
                        + reference + "</p>"
                        + note("You can follow your order in the Imototo app at any time. If something "
                                + "does not look right, use Help &amp; Support in the app."));
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
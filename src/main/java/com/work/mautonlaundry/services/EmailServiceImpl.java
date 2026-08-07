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
    public void sendBookingNotification(String email, String reference, String message) {
        // Every update used the same subject, so a customer's inbox filled with
        // identical lines and they had to open each one to learn anything.
        String subject = deriveSubject(message) + " - " + fromName;
        String body = buildBookingNotificationBody(reference, message);
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

    /**
     * @param reference the order's tracking number, or null when it could not
     *                  be resolved. Previously this took the booking id and
     *                  printed the first eight characters of the UUID as an
     *                  "Order reference" -- a code that exists nowhere else in
     *                  the product, so a customer quoting it to support was
     *                  quoting something unlookuppable. Omitted entirely rather
     *                  than shown as an internal id.
     */
    private String buildBookingNotificationBody(String reference, String message) {
        String referenceBlock = reference == null || reference.isBlank() ? ""
                : "<p style=\"margin:0 0 4px 0;font-size:13px;color:#6B7280;\">Order reference</p>"
                        + "<p style=\"margin:0 0 20px 0;font-size:15px;font-weight:600;color:#1A3A6B;\">"
                        + reference + "</p>";
        return emailShell(
                null,
                para(message)
                        + referenceBlock
                        + note("You can follow your order in the Imototo app at any time. If something "
                                + "does not look right, use Help &amp; Support in the app."));
    }

    /**
     * A role name a person would say out loud.
     *
     * <p>The templates each did {@code roleName.replace("_"," ").toLowerCase()}
     * inline, which is fine for LAUNDRY_AGENT and turns anything else we add
     * into whatever the enum happens to be called.
     */
    private String roleLabel(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "agent";
        }
        return switch (roleName.trim().toUpperCase()) {
            case "LAUNDRY_AGENT" -> "laundry partner";
            case "DELIVERY_AGENT" -> "delivery rider";
            case "ADMIN" -> "administrator";
            default -> roleName.replace("_", " ").toLowerCase();
        };
    }

    /** A labelled fact, for the admin-facing summary emails. */
    private String detail(String label, String value) {
        return "<p style=\"margin:0 0 6px 0;font-size:14px;color:#1A1A2E;\">"
                + "<span style=\"color:#6B7280;\">" + label + ":</span> <strong>"
                + value + "</strong></p>";
    }

    /** A reason or note that needs to stand out from the sentence around it. */
    private String calloutNote(String label, String text) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin:0 0 18px 0;\"><tr>"
                + "<td style=\"background:#F5F7FA;border-left:3px solid #1A3A6B;padding:12px 14px;\">"
                + "<p style=\"margin:0 0 2px 0;font-size:12px;font-weight:700;letter-spacing:0.4px;"
                + "color:#6B7280;\">" + label.toUpperCase() + "</p>"
                + "<p style=\"margin:0;font-size:14px;line-height:1.5;color:#1A1A2E;\">"
                + text + "</p></td></tr></table>";
    }

    private String contactLine(String adminTeamEmail, String lead) {
        if (adminTeamEmail == null || adminTeamEmail.isBlank()) {
            return "";
        }
        return note(lead + " <a href=\"mailto:" + adminTeamEmail
                + "\" style=\"color:#1A3A6B;\">" + adminTeamEmail + "</a>.");
    }

    private String buildAgentApplicationSubmittedBody(String roleName, int locationsCount, String adminTeamEmail) {
        boolean isLaundry = "LAUNDRY_AGENT".equalsIgnoreCase(roleName);
        String locations = locationsCount == 1 ? "one location" : locationsCount + " locations";
        return emailShell(
                "Application received",
                para("Thanks for applying to join Imototo as a " + roleLabel(roleName)
                        + ". We have your details and " + locations + ".")
                        + (isLaundry
                                ? para("Next, someone from our team will visit each location to "
                                        + "inspect it. Visits are unannounced and can happen on any "
                                        + "day, so please keep the premises ready.")
                                        + note("If a location does not pass inspection, the "
                                                + "application cannot go ahead.")
                                : para("Next, our team will review your details and come back to "
                                        + "you."))
                        + contactLine(adminTeamEmail, "Need to change something? Email us at"));
    }

    private String buildAdminAgentApplicationNotificationBody(String applicantEmail, String roleName, int locationsCount) {
        return emailShell(
                "New agent application",
                para("An application is waiting for review.")
                        + detail("Applicant", applicantEmail)
                        + detail("Applying as", roleLabel(roleName))
                        + detail("Locations", String.valueOf(locationsCount))
                        + note("Open the admin dashboard to approve or decline it."));
    }

    private String buildAgentApplicationApprovedBody(String roleName) {
        return emailShell(
                "You're approved",
                para("Your application to join Imototo as a " + roleLabel(roleName)
                        + " has been approved.")
                        + para("Sign in to the Imototo Ops app with the same email address and "
                                + "you can start taking work.")
                        + note("Go online in the app when you are ready to receive jobs. You will "
                                + "not be offered anything while you are offline."));
    }

    private String buildAgentApplicationRejectedBody(String roleName, String reason, String adminTeamEmail) {
        return emailShell(
                "About your application",
                para("Your application to join Imototo as a " + roleLabel(roleName)
                        + " was not approved this time.")
                        + (reason == null || reason.isBlank() ? "" : calloutNote("Reason", reason))
                        + contactLine(adminTeamEmail, "If you think this is a mistake, email us at"));
    }

    private String buildAgentDeactivationBody(String roleName, String reason, String adminTeamEmail) {
        return emailShell(
                "Your access has been paused",
                para("Your " + roleLabel(roleName) + " access on Imototo has been deactivated, so "
                        + "you will not be offered new jobs.")
                        + (reason == null || reason.isBlank() ? "" : calloutNote("Reason", reason))
                        + contactLine(adminTeamEmail, "To discuss this, email our team at"));
    }

    @Override
    @Async
    public void sendDiscountApprovedEmail(String email, String discountName) {
        String subject = "Your discount is active - " + fromName;
        sendEmail(email, subject, buildDiscountApprovedBody(discountName));
    }

    private String buildDiscountApprovedBody(String discountName) {
        // The name was passed in and never used, so every one of these said
        // "your discount code" and left the reader to guess which.
        String named = discountName == null || discountName.isBlank()
                ? "Your discount"
                : "Your discount, " + discountName + ",";
        return emailShell(
                "Your discount is active",
                para(named + " has been approved and is ready to use.")
                        + para("It will be applied when you enter the code on your next order.")
                        + note("Discounts apply to the order total before delivery."));
    }
}

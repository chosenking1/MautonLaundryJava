package com.work.mautonlaundry.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customer-facing emails were nine hand-rolled pages that had drifted into
 * different fonts, colours and button styles -- the verification button was
 * green, which belongs to no part of the brand. These pin the shared shell so
 * they cannot drift apart again.
 */
class EmailTemplateTest {

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl(null);
        ReflectionTestUtils.setField(service, "fromName", "Imototo");
    }

    private String build(String method, Object... args) {
        return (String) ReflectionTestUtils.invokeMethod(service, method, args);
    }

    private String verification() {
        return build("buildVerificationEmailBody", "https://api.test/verify?token=abc");
    }

    private String reset() {
        return build("buildPasswordResetEmailBody", "https://api.test/reset-password?token=abc");
    }

    private String welcome(String firstName) {
        return build("buildWelcomeEmailBody", firstName);
    }

    private String notification() {
        return build("buildBookingNotificationBody", "TRK1754429181234",
                "Your laundry has arrived at our facility.");
    }

    @Test
    void allCustomerEmailsShareTheBrandHeader() {
        for (String body : new String[] {verification(), reset(), notification(), welcome("Ada")}) {
            assertThat(body).contains("#1A3A6B");   // brand navy
            assertThat(body).contains("Imototo");
        }
    }

    @Test
    void noOffBrandColoursRemain() {
        // The verification button used to be #4CAF50 -- Material green, from no
        // part of this brand.
        for (String body : new String[] {verification(), reset(), notification(), welcome("Ada")}) {
            assertThat(body).doesNotContain("4CAF50");
        }
    }

    @Test
    void actionEmailsCarryBothAButtonAndACopyableLink() {
        // Some clients strip the button entirely; a link the user can paste is
        // the difference between a working reset and a support ticket.
        for (String body : new String[] {verification(), reset()}) {
            assertThat(body).contains("</a>");
            assertThat(body).contains("paste this into your browser");
        }
    }

    @Test
    void layoutUsesTablesSoItSurvivesMailClients() {
        // Gmail and Outlook strip stylesheets and do not implement flexbox.
        for (String body : new String[] {verification(), reset(), notification(), welcome("Ada")}) {
            assertThat(body).contains("role=\"presentation\"");
            assertThat(body).doesNotContain("display:flex");
            assertThat(body).doesNotContain("<style");
        }
    }

    @Test
    void theNotificationShowsTheRealTrackingNumber() {
        // It used to print the first eight characters of the booking's UUID as
        // an "Order reference" -- a code that appears nowhere else in the
        // product, so a customer quoting it to support quoted nothing.
        String body = notification();
        assertThat(body).contains("TRK1754429181234");
        assertThat(body).contains("Order reference");
    }

    @Test
    void anUnresolvableReferenceIsOmittedRatherThanFaked() {
        for (String missing : new String[] {null, "", "  "}) {
            String body = build("buildBookingNotificationBody", missing, "Your laundry is on its way.");
            assertThat(body).doesNotContain("Order reference");
            assertThat(body).contains("Your laundry is on its way.");
        }
    }

    @Test
    void subjectsComeFromTheMessageRatherThanBeingIdentical() {
        String subject = (String) ReflectionTestUtils.invokeMethod(
                service, "deriveSubject", "Your laundry has arrived at our facility.");
        assertThat(subject).isEqualTo("Your laundry has arrived at our facility");

        String fallback = (String) ReflectionTestUtils.invokeMethod(
                service, "deriveSubject", "");
        assertThat(fallback).isEqualTo("Order update");
    }

    @Test
    void theFooterCarriesTheTagline() {
        for (String body : new String[] {verification(), reset(), notification(), welcome("Ada")}) {
            assertThat(body).contains("Outsource the dirty work.");
            assertThat(body).doesNotContain("collected and returned");
        }
    }

    @Test
    void theWelcomeGreetsByFirstNameWhenThereIsOne() {
        assertThat(welcome("Ada")).contains("You&#39;re all set, Ada.".replace("&#39;", "'"));
    }

    @Test
    void theWelcomeDegradesRatherThanGreetingNobody() {
        // firstNameOf returns null for a user with no full_name, so this path is
        // reachable in production, and "Hi null" is worse than no name at all.
        for (String missing : new String[] {null, "", "   "}) {
            String body = welcome(missing);
            assertThat(body).contains("You're all set.");
            assertThat(body).doesNotContain("null");
        }
    }

    @Test
    void theWelcomeAsksForNothing() {
        // It is the one email with no action to complete; a button here would
        // imply the account is not finished.
        String body = welcome("Ada");
        assertThat(body).doesNotContain("paste this into your browser");
        assertThat(body).doesNotContain("<a href");
    }

    @Test
    void theWelcomeStepsSurviveOutlook() {
        String body = welcome("Ada");
        for (String step : new String[] {"Add your address", "Book a collection", "Follow it in the app"}) {
            assertThat(body).contains(step);
        }
        assertThat(body).doesNotContain("<ol");
        assertThat(body).doesNotContain("<ul");
    }

    // ---- the agent / admin templates --------------------------------------

    private String[] everyTemplate() {
        return new String[] {
            verification(), reset(), notification(), welcome("Ada"),
            build("buildAgentApplicationSubmittedBody", "LAUNDRY_AGENT", 2, "ops@imototo.com"),
            build("buildAdminAgentApplicationNotificationBody", "ada@example.com", "DELIVERY_AGENT", 1),
            build("buildAgentApplicationApprovedBody", "DELIVERY_AGENT"),
            build("buildAgentApplicationRejectedBody", "LAUNDRY_AGENT", "Premises failed inspection.", "ops@imototo.com"),
            build("buildAgentDeactivationBody", "DELIVERY_AGENT", "Repeated no-shows.", "ops@imototo.com"),
            build("buildDiscountApprovedBody", "WELCOME10"),
        };
    }

    @Test
    void everyTemplateIsOnTheSharedShell() {
        for (String body : everyTemplate()) {
            assertThat(body).contains("#1A3A6B");
            assertThat(body).contains("Outsource the dirty work.");
            assertThat(body).contains("role=\"presentation\"");
        }
    }

    @Test
    void noTemplateStillUsesTheOldBarePage() {
        for (String body : everyTemplate()) {
            assertThat(body).doesNotContain("Arial, sans-serif");
            assertThat(body).doesNotContain("Best regards,");
        }
    }

    @Test
    void noRoleReachesAnAgentAsAnEnum() {
        // Templates each did roleName.replace("_"," ").toLowerCase() inline.
        for (String body : everyTemplate()) {
            assertThat(body).doesNotContain("LAUNDRY_AGENT");
            assertThat(body).doesNotContain("DELIVERY_AGENT");
        }
        assertThat(build("buildAgentApplicationApprovedBody", "DELIVERY_AGENT"))
                .contains("delivery rider");
    }

    @Test
    void anUnknownRoleDegradesRatherThanShowingTheEnum() {
        assertThat(build("buildAgentApplicationApprovedBody", "SOME_NEW_ROLE"))
                .contains("some new role")
                .doesNotContain("SOME_NEW_ROLE");
        assertThat(build("buildAgentApplicationApprovedBody", (String) null))
                .contains("agent")
                .doesNotContain("null");
    }

    @Test
    void theDiscountEmailNamesTheDiscount() {
        // The name was passed in and never used, so every one of these said
        // "your discount code" and left the reader to guess which.
        assertThat(build("buildDiscountApprovedBody", "WELCOME10")).contains("WELCOME10");
        assertThat(build("buildDiscountApprovedBody", (String) null))
                .contains("Your discount")
                .doesNotContain("null");
    }

    @Test
    void aMissingReasonLeavesNoEmptyCallout() {
        for (String blank : new String[] {null, "", "  "}) {
            String body = build("buildAgentApplicationRejectedBody", "LAUNDRY_AGENT", blank, "ops@imototo.com");
            assertThat(body).doesNotContain("REASON");
        }
        assertThat(build("buildAgentApplicationRejectedBody", "LAUNDRY_AGENT", "Failed inspection.", "ops@imototo.com"))
                .contains("REASON")
                .contains("Failed inspection.");
    }

    @Test
    void aMissingContactEmailLeavesNoDanglingSentence() {
        for (String blank : new String[] {null, "", "  "}) {
            String body = build("buildAgentDeactivationBody", "DELIVERY_AGENT", "Reason.", blank);
            assertThat(body).doesNotContain("email us at");
            assertThat(body).doesNotContain("email our team at");
            assertThat(body).doesNotContain("mailto:");
        }
    }

    @Test
    void oneLocationDoesNotReadAsOneLocations() {
        assertThat(build("buildAgentApplicationSubmittedBody", "LAUNDRY_AGENT", 1, "ops@imototo.com"))
                .contains("one location")
                .doesNotContain("1 locations");
        assertThat(build("buildAgentApplicationSubmittedBody", "LAUNDRY_AGENT", 3, "ops@imototo.com"))
                .contains("3 locations");
    }
}

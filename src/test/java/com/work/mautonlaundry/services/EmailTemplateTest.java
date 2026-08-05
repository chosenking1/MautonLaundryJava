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
        return build("buildBookingNotificationBody",
                "0af6dfbd-e30c-4349-887b-14f54c4912de",
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
    void theNotificationShowsAShortReferenceNotTheRawUuid() {
        String body = notification();
        assertThat(body).contains("0AF6DFBD");
        assertThat(body).doesNotContain("0af6dfbd-e30c-4349-887b-14f54c4912de");
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
}

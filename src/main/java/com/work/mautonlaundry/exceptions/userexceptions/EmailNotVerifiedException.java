package com.work.mautonlaundry.exceptions.userexceptions;

/**
 * Login with a correct password but an unverified email. Thrown only AFTER
 * authentication succeeds, so it never reveals whether an email exists to a
 * caller who does not know the password. Mapped to 403 EMAIL_NOT_VERIFIED so
 * clients can route the user to a resend-verification screen instead of
 * showing a dead-end error.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}

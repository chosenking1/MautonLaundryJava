package com.work.mautonlaundry.services;

/**
 * What happened when a verification link was opened.
 *
 * <p>Used to be a boolean, which folded "already confirmed", "expired" and
 * "never existed" into one "Invalid or expired token" -- so a customer who
 * tapped the link twice was told it was broken, and one whose link had lapsed
 * was not told that a resend would fix it. Each outcome now gets its own
 * screen and next step.
 */
public enum EmailVerificationOutcome {
    /** This click confirmed the address. */
    VERIFIED,
    /** The address was already confirmed; nothing changed. */
    ALREADY_VERIFIED,
    /** A real link for an unconfirmed address, past its expiry. Resendable from the token. */
    EXPIRED,
    /** Unknown, superseded, or not a verification token at all. */
    INVALID;

    /** True when the address is confirmed after this call, however it got there. */
    public boolean isConfirmed() {
        return this == VERIFIED || this == ALREADY_VERIFIED;
    }
}

package com.work.mautonlaundry.util;

/**
 * The one phone-number rule, shared by every request that takes a customer's or
 * agent's number.
 *
 * <p>Nigerian mobile numbers only: 11 digits starting 07, 08 or 09 (the form
 * people write), or the same number with the leading 0 replaced by 234 / +234.
 * The old rule was "7-15 digits", which let a 7-digit number through -- one a
 * rider could never call.
 */
public final class PhoneNumbers {

    /** For {@code @Pattern}: annotations need a compile-time constant. Strip spaces/dashes before matching. */
    public static final String NIGERIAN_MOBILE_REGEX = "^(?:0|\\+?234)[789][0-9]{9}$";

    public static final String NIGERIAN_MOBILE_MESSAGE =
            "Enter a valid 11-digit phone number, e.g. 08012345678";

    private PhoneNumbers() {
    }
}

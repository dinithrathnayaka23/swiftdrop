package com.swiftdrop.identity.dto.request;

/** Input rules shared by the auth requests. Kept in line with frontend/src/utils/validation.ts. */
final class ValidationPatterns {

    /** Letters from any language, spaces, apostrophes, dots and hyphens. */
    static final String PERSON_NAME = "^\\p{L}[\\p{L}\\p{M}\\s.'-]*$";

    /** E.164 phone number, e.g. +94712345678. */
    static final String PHONE_E164 = "^\\+[1-9]\\d{6,14}$";

    /** At least one letter and one digit, no leading/trailing whitespace (length via @Size). */
    static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)\\S(.*\\S)?$";

    /**
     * Sri Lankan company registration (Companies Act No. 7 of 2007): PV (private), PB (public) or
     * GA (guarantee) + 8 digits, e.g. PV00234567. Case-insensitive, optional space or hyphen after
     * the prefix; stored normalised by MerchantService.
     */
    static final String REGISTRATION_NO = "(?i)^\\s*(PV|PB|GA)[ -]?\\d{8}\\s*$";

    static final String REGISTRATION_NO_MESSAGE =
            "Enter a valid registration number: PV, PB or GA followed by 8 digits (e.g. PV00234567)";

    static final String PASSWORD_MESSAGE =
            "Password must include at least one letter and one number and cannot start or end with a space";

    private ValidationPatterns() {
    }
}

package com.swiftdrop.dispatch.dto.request;

/** Input rules for the courier requests. */
final class CourierRules {

    /** Letters from any language, spaces, apostrophes, dots and hyphens. */
    static final String PERSON_NAME = "^\\p{L}[\\p{L}\\p{M}\\s.'-]*$";

    /** E.164 phone number, e.g. +94712345678. */
    static final String PHONE_E164 = "^\\+[1-9]\\d{6,14}$";

    /** At least one letter and one digit, no leading/trailing whitespace (length via @Size). */
    static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)\\S(.*\\S)?$";

    static final String PASSWORD_MESSAGE =
            "Password must include at least one letter and one number and cannot start or end with a space";

    /** Letters, digits, spaces and hyphens, e.g. "WP CAB-1234" or "ABC-1234". */
    static final String VEHICLE_REGISTRATION = "^[A-Za-z0-9][A-Za-z0-9 -]*$";

    static final String VEHICLE_REGISTRATION_MESSAGE =
            "Vehicle registration can only contain letters, numbers, spaces and hyphens";

    static final int MAX_SERVICE_AREAS = 50;

    private CourierRules() {
    }
}

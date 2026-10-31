package com.swiftdrop.common.exception;

/** Machine-readable error codes shared by all modules. Add new codes here rather than inventing ad-hoc strings. */
public enum ErrorCode {
    VALIDATION_FAILED,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    /** Signed in correctly, but the account is waiting for admin approval (merchants). */
    ACCOUNT_PENDING_APPROVAL,
    /** Signed in correctly, but the account is suspended, deactivated or rejected. */
    ACCOUNT_DISABLED,
    /** Password reset link is invalid, expired or already used. */
    INVALID_RESET_TOKEN,
    /** Correct password, but this sign-in page is for other roles (e.g. a courier on the main page). */
    WRONG_SIGN_IN_PAGE,
    /** Correct password, but the email address hasn't been confirmed yet. */
    EMAIL_NOT_VERIFIED,
    /** Email verification link is invalid or expired. */
    INVALID_VERIFICATION_TOKEN,
    /** Google sign-in is not configured, or Google could not be reached. */
    GOOGLE_SIGN_IN_UNAVAILABLE,
    /** Rate limit hit; see the Retry-After header. */
    TOO_MANY_REQUESTS,
    /** A dependency (database, Redis) is unreachable; retrying shortly usually works. */
    SERVICE_UNAVAILABLE,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}

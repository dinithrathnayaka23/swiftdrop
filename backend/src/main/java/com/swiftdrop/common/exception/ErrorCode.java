package com.swiftdrop.common.exception;

/** Machine-readable error codes shared by all modules. Add new codes here rather than inventing ad-hoc strings. */
public enum ErrorCode {
    VALIDATION_FAILED,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}

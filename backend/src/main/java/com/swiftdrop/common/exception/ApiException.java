package com.swiftdrop.common.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for business exceptions. The message is returned to the client,
 * so it must be safe to show (no personal data, no internals).
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    /** Optional per-field errors (e.g. "email already registered") so the UI can mark the input. */
    private final List<ErrorResponse.FieldError> fieldErrors;

    protected ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, List.of());
    }

    protected ApiException(HttpStatus status, ErrorCode code, String message,
                           List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }
}

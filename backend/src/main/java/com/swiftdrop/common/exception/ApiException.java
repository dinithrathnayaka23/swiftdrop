package com.swiftdrop.common.exception;

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

    protected ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}

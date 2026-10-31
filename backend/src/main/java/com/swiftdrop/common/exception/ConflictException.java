package com.swiftdrop.common.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private ConflictException(String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message, fieldErrors);
    }

    /** A conflict caused by one input, e.g. {@code forField("email", "An account with this email already exists")}. */
    public static ConflictException forField(String field, String message) {
        return new ConflictException(message, List.of(new ErrorResponse.FieldError(field, message)));
    }
}

package com.swiftdrop.identity.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends ApiException {

    public InvalidResetTokenException() {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESET_TOKEN,
                "This reset link is invalid or has expired. Please request a new one.");
    }
}

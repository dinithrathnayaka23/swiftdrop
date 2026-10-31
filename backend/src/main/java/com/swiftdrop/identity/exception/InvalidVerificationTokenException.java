package com.swiftdrop.identity.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 400: the email verification link is invalid, expired, or for an address the account no longer has. */
public class InvalidVerificationTokenException extends ApiException {

    public InvalidVerificationTokenException() {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_VERIFICATION_TOKEN,
                "This confirmation link is invalid or has expired. Please request a new one.");
    }
}

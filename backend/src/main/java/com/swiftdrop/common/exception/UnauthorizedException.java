package com.swiftdrop.common.exception;

import org.springframework.http.HttpStatus;

/** 401: the caller could not be authenticated (e.g. wrong email or password). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }
}

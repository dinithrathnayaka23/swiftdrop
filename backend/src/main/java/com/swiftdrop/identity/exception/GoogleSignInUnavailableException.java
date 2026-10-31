package com.swiftdrop.identity.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 503: Google sign-in is not configured on this server, or Google could not be reached. */
public class GoogleSignInUnavailableException extends ApiException {

    public GoogleSignInUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE,
                "Google sign-in is not available right now. Please use your email and password.");
    }
}

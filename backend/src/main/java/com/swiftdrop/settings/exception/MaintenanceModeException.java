package com.swiftdrop.settings.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 503 while maintenance mode is on: only the admin can use the system. */
public class MaintenanceModeException extends ApiException {

    public static final String MESSAGE =
            "SwiftDrop is down for scheduled maintenance. Please try again a little later.";

    public MaintenanceModeException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.MAINTENANCE_MODE, MESSAGE);
    }
}

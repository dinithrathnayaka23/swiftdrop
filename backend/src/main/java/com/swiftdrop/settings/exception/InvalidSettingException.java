package com.swiftdrop.settings.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.exception.ErrorResponse;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 400 for a setting that passed the basic checks but is not allowed, e.g. an unknown time zone. */
public class InvalidSettingException extends ApiException {

    public InvalidSettingException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message,
                List.of(new ErrorResponse.FieldError(field, message)));
    }
}

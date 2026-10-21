package com.swiftdrop.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by every endpoint. Never put stack traces,
 * SQL, internal class names or personal data in here.
 *
 * <pre>
 * {
 *   "timestamp": "2026-01-01T10:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "code": "VALIDATION_FAILED",
 *   "message": "Request validation failed",
 *   "path": "/api/v1/parcels",
 *   "fieldErrors": [{ "field": "weightKg", "message": "must be greater than 0" }]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}

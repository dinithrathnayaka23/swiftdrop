package com.swiftdrop.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A failed delivery: one of the failure reasons ({@code GET /courier/failure-reasons}) and an
 * optional note. Some reasons (e.g. OTHER) require the note; the service checks that.
 */
public record ReportFailureRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = 40, message = "Reason must be at most 40 characters")
        String reasonCode,
        @Size(max = ReportFailureRequest.MAX_NOTE, message = "Note must be at most "
                + ReportFailureRequest.MAX_NOTE + " characters")
        String note) {

    public static final int MAX_NOTE = 500;
}

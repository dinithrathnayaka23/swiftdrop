package com.swiftdrop.realtime.controller;

import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.exception.ErrorResponse;
import com.swiftdrop.common.exception.RateLimitExceededException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Responses shared by the stream endpoints (public and admin). */
final class StreamResponses {

    /** Tells nginx not to buffer the response, so each event reaches the browser at once. */
    static final String NO_PROXY_BUFFERING_HEADER = "X-Accel-Buffering";

    private StreamResponses() {
    }

    /** An open event stream. */
    static ResponseEntity<SseEmitter> stream(SseEmitter emitter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header(NO_PROXY_BUFFERING_HEADER, "no")
                .body(emitter);
    }

    /**
     * An error in the standard error body, as the global handler writes it. The content type is set
     * up front: a browser that only accepts {@code text/event-stream} would otherwise get a 500,
     * because the JSON body can't be negotiated.
     */
    static ResponseEntity<ErrorResponse> error(HttpStatus status, ErrorCode code, String message, String path,
                                               List<ErrorResponse.FieldError> fieldErrors, Exception cause) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (cause instanceof RateLimitExceededException limit) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limit.getRetryAfterSeconds()));
        }
        return response.body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code.name(),
                message, path, fieldErrors));
    }
}

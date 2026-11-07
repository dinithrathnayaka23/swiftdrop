package com.swiftdrop.realtime.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.exception.ErrorResponse;
import com.swiftdrop.realtime.service.LiveStreamHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live status updates of every parcel, for the admin Deliveries Tracking screens. Same events as
 * the public stream; the browser sends the admin's bearer token (it reads the stream with fetch).
 */
@Tag(name = "Admin: Deliveries Tracking", description = "Every delivery with its live status")
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/live")
@RequiredArgsConstructor
public class AdminLiveStreamController {

    private final LiveStreamHub streamHub;

    @Operation(summary = "Follow every parcel's status live",
            description = "Server-sent events: `event: status` with a LiveStatusUpdate for any parcel, and a "
                    + "`: ping` comment every 25 seconds. Load the table first; apply only newer statusVersions.")
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    @GetMapping("/stream")
    public ResponseEntity<SseEmitter> stream(HttpServletRequest request) {
        return StreamResponses.stream(streamHub.openAdminStream(request.getRemoteAddr()));
    }

    /** 429 (too many open streams) as the standard JSON error body, whatever the browser accepts. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleError(ApiException ex, HttpServletRequest request) {
        return StreamResponses.error(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(),
                ex.getFieldErrors(), ex);
    }

    /** 403 for a signed-in user who isn't an admin, in the same way. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return StreamResponses.error(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied",
                request.getRequestURI(), List.of(), ex);
    }
}

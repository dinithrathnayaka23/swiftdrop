package com.swiftdrop.dispatch.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.dispatch.dto.request.CloseDeliveryRequest;
import com.swiftdrop.dispatch.dto.request.FailedDeliveryScope;
import com.swiftdrop.dispatch.dto.request.RetryDeliveryRequest;
import com.swiftdrop.dispatch.dto.response.DefaultFailedActionResponse;
import com.swiftdrop.dispatch.dto.response.FailedDecisionResponse;
import com.swiftdrop.dispatch.dto.response.FailedDeliveryResponse;
import com.swiftdrop.dispatch.service.FailedDeliveryService;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin Failed Deliveries screen: the queue, and retrying or closing a failed attempt. */
@RestController
@RequestMapping(AdminFailedDeliveryController.PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminFailedDeliveryController {

    static final String PATH = ApiPaths.API_V1 + "/admin/dispatch";

    private final FailedDeliveryService failedDeliveryService;

    /**
     * {@code scope=ATTENTION} (default): parcels in FAILED_ATTEMPT waiting for a decision;
     * {@code scope=CLOSED}: parcels closed as FAILED. Newest booking first, {@code size} 1-50.
     */
    @GetMapping("/failed-deliveries")
    public PagedModel<FailedDeliveryResponse> list(
            @RequestParam(defaultValue = "ATTENTION") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkPage(page, size);
        return new PagedModel<>(failedDeliveryService.list(scopeOf(scope), page, size));
    }

    /**
     * Retries a failed attempt: the parcel goes back to CREATED (Unassigned). Assign a courier with
     * the normal assign call afterwards. 409 unless the parcel is FAILED_ATTEMPT.
     */
    @PostMapping("/parcels/{parcelId}/retry")
    public FailedDecisionResponse retry(@PathVariable UUID parcelId,
                                        @Valid @RequestBody(required = false) RetryDeliveryRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser admin) {
        return failedDeliveryService.retry(parcelId, request == null ? null : request.note(),
                UUID.fromString(admin.userId()));
    }

    /** Closes a failed attempt as FAILED (end state) with a reason. 409 unless the parcel is FAILED_ATTEMPT. */
    @PostMapping("/parcels/{parcelId}/close")
    public FailedDecisionResponse close(@PathVariable UUID parcelId, @Valid @RequestBody CloseDeliveryRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser admin) {
        return failedDeliveryService.close(parcelId, request.reason(), UUID.fromString(admin.userId()));
    }

    /** The System Settings default action, to pre-select Retry or pre-fill the close reason. */
    @GetMapping("/failed-deliveries/default-action")
    public DefaultFailedActionResponse defaultAction() {
        return failedDeliveryService.defaultAction();
    }

    static FailedDeliveryScope scopeOf(String scope) {
        try {
            return FailedDeliveryScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("scope must be ATTENTION or CLOSED");
        }
    }

    static void checkPage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > FailedDeliveryService.MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + FailedDeliveryService.MAX_PAGE_SIZE);
        }
    }
}

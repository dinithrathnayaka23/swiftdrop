package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import com.swiftdrop.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.swiftdrop.delivery.dto.response.CompletedDeliveryResponse;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.TodaysDeliveriesResponse;
import com.swiftdrop.delivery.service.CompletedDeliveryService;
import com.swiftdrop.delivery.service.CourierDeliveryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The courier app: Today's Deliveries, Completed Deliveries, Delivery Details, the scanner's
 * look-up and Update Status. Own deliveries only.
 */
@RestController
@RequestMapping(CourierDeliveryController.PATH)
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class CourierDeliveryController {

    static final String PATH = ApiPaths.API_V1 + "/courier/deliveries";

    private final CourierDeliveryService courierDeliveryService;
    private final CompletedDeliveryService completedDeliveryService;

    /** The courier's open deliveries, oldest assignment first, and the counts above the list. */
    @GetMapping("/today")
    public TodaysDeliveriesResponse today(@AuthenticationPrincipal AuthenticatedUser courier) {
        return courierDeliveryService.today(id(courier));
    }

    /**
     * The courier's deliveries and failed attempts, newest first. {@code from} and {@code to} are
     * inclusive dates (yyyy-MM-dd) in the admin's time zone; without them, the last 7 days. Spring's
     * paged JSON; 400 for a bad page, size, date or outcome, or {@code to} before {@code from}.
     */
    @GetMapping("/completed")
    public PagedModel<CompletedDeliveryResponse> completed(
            @AuthenticationPrincipal AuthenticatedUser courier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DeliveryOutcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return new PagedModel<>(completedDeliveryService.list(id(courier), from, to, outcome, page, size));
    }

    /** One delivery with its history; 404 when it doesn't exist or isn't assigned to the courier. */
    @GetMapping("/{parcelId}")
    public CourierDeliveryDetailResponse get(@AuthenticationPrincipal AuthenticatedUser courier,
                                             @PathVariable UUID parcelId) {
        return courierDeliveryService.get(parcelId, id(courier));
    }

    /** The scanner's look-up (case and spaces ignored); 404 when it isn't one of the courier's deliveries. */
    @GetMapping("/by-tracking-number/{trackingNumber}")
    public CourierDeliveryDetailResponse getByTrackingNumber(@AuthenticationPrincipal AuthenticatedUser courier,
                                                             @PathVariable String trackingNumber) {
        return courierDeliveryService.getByTrackingNumber(trackingNumber, id(courier));
    }

    /**
     * Picked up, in transit or out for delivery; returns the updated delivery. 400 for DELIVERED or
     * a failure (they have their own endpoints), 404 when it isn't the courier's, 409 when the parcel
     * can't take that step now (for example the same tap sent twice).
     */
    @PostMapping("/{parcelId}/status")
    public CourierDeliveryDetailResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser courier,
                                                      @PathVariable UUID parcelId,
                                                      @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return courierDeliveryService.updateStatus(parcelId, id(courier), request);
    }

    private static UUID id(AuthenticatedUser user) {
        return UUID.fromString(user.userId());
    }
}

package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.TodaysDeliveriesResponse;
import com.swiftdrop.delivery.service.CourierDeliveryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The courier app: Today's Deliveries, Delivery Details and the scanner's look-up. Own deliveries only. */
@RestController
@RequestMapping(CourierDeliveryController.PATH)
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class CourierDeliveryController {

    static final String PATH = ApiPaths.API_V1 + "/courier/deliveries";

    private final CourierDeliveryService courierDeliveryService;

    /** The courier's open deliveries, oldest assignment first, and the counts above the list. */
    @GetMapping("/today")
    public TodaysDeliveriesResponse today(@AuthenticationPrincipal AuthenticatedUser courier) {
        return courierDeliveryService.today(id(courier));
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

    private static UUID id(AuthenticatedUser user) {
        return UUID.fromString(user.userId());
    }
}

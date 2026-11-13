package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.request.ReportFailureRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.FailureReasonResponse;
import com.swiftdrop.delivery.service.DeliveryFailureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The courier app's Failed Delivery form: the reasons and reporting a failure. Own deliveries only. */
@RestController
@RequestMapping(CourierFailureController.PATH)
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class CourierFailureController {

    static final String PATH = ApiPaths.API_V1 + "/courier";

    private final DeliveryFailureService deliveryFailureService;

    /** The reasons a courier can pick, in display order. */
    @GetMapping("/failure-reasons")
    public List<FailureReasonResponse> reasons() {
        return deliveryFailureService.reasons();
    }

    /**
     * Marks the delivery as failed; returns the updated delivery. 400 for an unknown reason or a
     * missing required note, 404 when it isn't the courier's, 409 when the parcel can't fail now.
     */
    @PostMapping("/deliveries/{parcelId}/fail")
    public CourierDeliveryDetailResponse reportFailure(@AuthenticationPrincipal AuthenticatedUser courier,
                                                       @PathVariable UUID parcelId,
                                                       @Valid @RequestBody ReportFailureRequest request) {
        return deliveryFailureService.reportFailure(parcelId, UUID.fromString(courier.userId()), request);
    }
}

package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.request.DeliverRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.service.DeliveryProofService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The courier app's Proof of Delivery screen. Own deliveries only. */
@RestController
@RequestMapping(CourierProofController.PATH)
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class CourierProofController {

    static final String PATH = ApiPaths.API_V1 + "/courier/deliveries";

    private final DeliveryProofService deliveryProofService;

    /**
     * Marks the delivery as DELIVERED; {@code multipart/form-data} with {@code recipientName},
     * optional {@code note} and optional {@code photo} (JPEG/PNG). Returns the updated delivery.
     * 400 for a missing name or a photo that isn't a readable image, 404 when it isn't the courier's,
     * 409 when the parcel isn't out for delivery, 413 when the upload is too large.
     */
    @PostMapping(value = "/{parcelId}/deliver", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourierDeliveryDetailResponse deliver(@AuthenticationPrincipal AuthenticatedUser courier,
                                                 @PathVariable UUID parcelId,
                                                 @Valid @ModelAttribute DeliverRequest request) {
        return deliveryProofService.deliver(parcelId, UUID.fromString(courier.userId()), request);
    }
}

package com.swiftdrop.delivery.dto.request;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A plain step on the way to the recipient: PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY. DELIVERED
 * and FAILED have their own endpoints (proof of delivery and the failed-delivery form).
 */
public record UpdateDeliveryStatusRequest(
        @NotNull(message = "Status is required") ParcelStatusCode status,
        @Size(max = UpdateDeliveryStatusRequest.MAX_NOTE, message = "Note must be at most "
                + UpdateDeliveryStatusRequest.MAX_NOTE + " characters")
        String note) {

    public static final int MAX_NOTE = 500;
}

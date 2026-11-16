package com.swiftdrop.delivery.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One row of the courier's Today's Deliveries.
 *
 * @param dropoff        the recipient and where to deliver
 * @param pickup         where to collect the parcel; only while it is still CREATED, otherwise {@code null}
 * @param allowedActions the statuses the courier may move the parcel to next, in lifecycle order
 *                       (from {@link ParcelStatusCode#nextStatuses()}); empty once delivered or failed
 */
public record CourierDeliveryItemResponse(
        UUID parcelId,
        String trackingNumber,
        CourierAddressResponse dropoff,
        CourierAddressResponse pickup,
        ParcelType parcelType,
        BigDecimal weightKg,
        String deliveryInstructions,
        ParcelStatusCode currentStatus,
        List<ParcelStatusCode> allowedActions) {
}

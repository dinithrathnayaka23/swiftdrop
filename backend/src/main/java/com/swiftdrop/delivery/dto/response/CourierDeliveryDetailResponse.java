package com.swiftdrop.delivery.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Delivery Details in the courier app: both stops, the parcel, what the courier may do next and
 * the status history.
 *
 * @param allowedActions the statuses the courier may move the parcel to next, in lifecycle order
 * @param bookedAt       when the parcel was booked
 * @param timeline       status changes, oldest first
 */
public record CourierDeliveryDetailResponse(
        UUID parcelId,
        String trackingNumber,
        CourierAddressResponse dropoff,
        CourierAddressResponse pickup,
        ParcelType parcelType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String contentDescription,
        String deliveryInstructions,
        ParcelStatusCode currentStatus,
        List<ParcelStatusCode> allowedActions,
        Instant bookedAt,
        List<CourierTimelineEntryResponse> timeline) {
}

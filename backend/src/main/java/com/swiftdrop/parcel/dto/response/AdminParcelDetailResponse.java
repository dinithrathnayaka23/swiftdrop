package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One parcel on the admin Parcels screen (details panel).
 *
 * @param serviceType    {@code null}: there is one service level for now
 * @param itemCount      always 1: one parcel per booking
 * @param paymentMethod  {@code null} until payments exist
 * @param recipientEmail {@code null}: addresses have no email
 * @param history        oldest first
 */
public record AdminParcelDetailResponse(
        UUID id,
        String trackingNumber,
        String merchantName,
        String recipientName,
        String destination,
        BigDecimal weightKg,
        ParcelStatusCode status,
        String dispatchState,
        Instant createdAt,
        String serviceType,
        ParcelType parcelType,
        String contentDescription,
        int itemCount,
        String paymentMethod,
        String recipientPhone,
        String recipientEmail,
        AddressResponse dropoffAddress,
        String deliveryInstructions,
        List<StatusHistoryItemResponse> history,
        List<AdminParcelNoteResponse> notes) {
}

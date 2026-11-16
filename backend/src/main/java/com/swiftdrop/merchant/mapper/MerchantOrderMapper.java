package com.swiftdrop.merchant.mapper;

import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import java.util.List;

/** Maps the parcel module's views to merchant portal responses, keeping only what a merchant needs. */
public final class MerchantOrderMapper {

    private MerchantOrderMapper() {
    }

    public static MerchantOrderSummaryResponse toSummary(ParcelDetails parcel) {
        AddressDetails dropoff = parcel.dropoffAddress();
        return new MerchantOrderSummaryResponse(parcel.id(), parcel.trackingNumber(),
                parcel.externalOrderReference(),
                dropoff == null ? null : dropoff.contactName(),
                dropoff == null ? null : dropoff.city(),
                parcel.parcelType(), parcel.currentStatus(), parcel.quotedFee(), parcel.feeCurrency(),
                parcel.createdAt());
    }

    public static MerchantOrderDetailResponse toDetail(ParcelDetails parcel, List<StatusHistoryEntry> history) {
        return new MerchantOrderDetailResponse(parcel.id(), parcel.trackingNumber(), parcel.externalOrderReference(),
                toAddress(parcel.pickupAddress()), toAddress(parcel.dropoffAddress()), parcel.parcelType(),
                parcel.weightKg(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm(),
                parcel.contentDescription(), parcel.deliveryInstructions(), parcel.currentStatus(),
                parcel.quotedFee(), parcel.feeCurrency(), parcel.createdAt(), parcel.updatedAt(),
                history.stream().map(MerchantOrderMapper::toTimelineEntry).toList());
    }

    private static MerchantOrderDetailResponse.Address toAddress(AddressDetails address) {
        if (address == null) {
            return null;
        }
        return new MerchantOrderDetailResponse.Address(address.contactName(), address.contactPhone(),
                address.addressLine1(), address.addressLine2(), address.city(), address.district(),
                address.postalCode());
    }

    private static MerchantOrderDetailResponse.TimelineEntry toTimelineEntry(StatusHistoryEntry entry) {
        return new MerchantOrderDetailResponse.TimelineEntry(entry.status(), entry.changedAt(), entry.note(),
                entry.failureReason(), entry.recipientName());
    }
}

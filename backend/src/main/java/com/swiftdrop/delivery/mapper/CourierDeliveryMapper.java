package com.swiftdrop.delivery.mapper;

import com.swiftdrop.delivery.dto.response.CourierAddressResponse;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.CourierDeliveryItemResponse;
import com.swiftdrop.delivery.dto.response.CourierTimelineEntryResponse;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.util.List;

/** Parcel module views to the courier app's responses, with only the fields a courier needs. */
public final class CourierDeliveryMapper {

    private CourierDeliveryMapper() {
    }

    /** A row of Today's Deliveries. The pickup stop is only included while the parcel waits for pickup. */
    public static CourierDeliveryItemResponse toItem(ParcelDetails parcel) {
        return new CourierDeliveryItemResponse(
                parcel.id(),
                parcel.trackingNumber(),
                toAddress(parcel.dropoffAddress()),
                parcel.currentStatus() == ParcelStatusCode.CREATED ? toAddress(parcel.pickupAddress()) : null,
                parcel.parcelType(),
                parcel.weightKg(),
                parcel.deliveryInstructions(),
                parcel.currentStatus(),
                allowedActions(parcel.currentStatus()));
    }

    public static CourierDeliveryDetailResponse toDetail(ParcelDetails parcel, List<StatusHistoryEntry> history) {
        return new CourierDeliveryDetailResponse(
                parcel.id(),
                parcel.trackingNumber(),
                toAddress(parcel.dropoffAddress()),
                toAddress(parcel.pickupAddress()),
                parcel.parcelType(),
                parcel.weightKg(),
                parcel.lengthCm(),
                parcel.widthCm(),
                parcel.heightCm(),
                parcel.contentDescription(),
                parcel.deliveryInstructions(),
                parcel.currentStatus(),
                allowedActions(parcel.currentStatus()),
                parcel.createdAt(),
                history.stream().map(CourierDeliveryMapper::toTimelineEntry).toList());
    }

    /** The parcel module's transition rules, in lifecycle order (the set is an EnumSet). */
    public static List<ParcelStatusCode> allowedActions(ParcelStatusCode current) {
        return List.copyOf(current.nextStatuses());
    }

    private static CourierAddressResponse toAddress(AddressDetails address) {
        if (address == null) {
            return null;
        }
        return new CourierAddressResponse(
                address.contactName(),
                address.contactPhone(),
                address.addressLine1(),
                address.addressLine2(),
                address.city(),
                address.district(),
                address.postalCode());
    }

    private static CourierTimelineEntryResponse toTimelineEntry(StatusHistoryEntry entry) {
        return new CourierTimelineEntryResponse(entry.status(), entry.changedAt(), entry.note(), entry.failureReason());
    }
}

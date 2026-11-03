package com.swiftdrop.parcel.mapper;

import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.Address;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;

public final class ParcelMapper {

    private ParcelMapper() {
    }

    public static ParcelDetails toDetails(Parcel parcel) {
        return new ParcelDetails(parcel.getId(), parcel.getTrackingNumber(), parcel.getCustomerUserId(),
                parcel.getMerchantId(), parcel.getExternalOrderReference(),
                toDetails(parcel.getPickupAddress()), toDetails(parcel.getDropoffAddress()),
                parcel.getParcelType(), parcel.getWeightKg(), parcel.getLengthCm(), parcel.getWidthCm(), parcel.getHeightCm(),
                parcel.getContentDescription(), parcel.getDeliveryInstructions(),
                ParcelStatusCode.valueOf(parcel.getCurrentStatus().getStatusCode()),
                parcel.getPricingRuleId(), parcel.getQuotedFee(), parcel.getFeeCurrency(),
                parcel.getCreatedAt(), parcel.getUpdatedAt());
    }

    public static StatusHistoryEntry toEntry(ParcelStatusHistory history) {
        ParcelStatus previous = history.getPreviousStatus();
        return new StatusHistoryEntry(history.getId(), history.getParcel().getId(),
                previous == null ? null : ParcelStatusCode.valueOf(previous.getStatusCode()),
                ParcelStatusCode.valueOf(history.getStatus().getStatusCode()), history.getChangedByUserId(),
                history.getNote(), history.getFailureReason(), history.getRecipientName(),
                history.getProofFileUrl(), history.getChangedAt());
    }

    public static AddressDetails toDetails(Address address) {
        return new AddressDetails(address.getId(), address.getContactName(), address.getContactPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getDistrict(),
                address.getPostalCode(), address.getZoneId());
    }
}

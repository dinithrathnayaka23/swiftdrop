package com.swiftdrop.parcel.mapper;

import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.Address;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatusCode;

public final class ParcelMapper {

    private ParcelMapper() {
    }

    public static ParcelDetails toDetails(Parcel parcel) {
        return new ParcelDetails(parcel.getId(), parcel.getTrackingNumber(), parcel.getCustomerUserId(),
                parcel.getMerchantId(), parcel.getExternalOrderReference(),
                toDetails(parcel.getPickupAddress()), toDetails(parcel.getDropoffAddress()),
                parcel.getWeightKg(), parcel.getLengthCm(), parcel.getWidthCm(), parcel.getHeightCm(),
                parcel.getContentDescription(), parcel.getDeliveryInstructions(),
                ParcelStatusCode.valueOf(parcel.getCurrentStatus().getStatusCode()),
                parcel.getPricingRuleId(), parcel.getQuotedFee(), parcel.getFeeCurrency(),
                parcel.getCreatedAt(), parcel.getUpdatedAt());
    }

    public static AddressDetails toDetails(Address address) {
        return new AddressDetails(address.getId(), address.getContactName(), address.getContactPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getDistrict(),
                address.getPostalCode(), address.getZoneId());
    }
}

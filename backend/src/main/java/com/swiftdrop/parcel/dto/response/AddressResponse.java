package com.swiftdrop.parcel.dto.response;

/** A pickup or drop-off address on a parcel screen. */
public record AddressResponse(
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String postalCode) {
}

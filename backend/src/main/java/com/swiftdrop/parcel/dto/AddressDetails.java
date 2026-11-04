package com.swiftdrop.parcel.dto;

import java.util.UUID;

/** A saved pickup or drop-off address, as part of {@link ParcelDetails}. Contains personal data. */
public record AddressDetails(
        UUID id,
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String postalCode,
        UUID zoneId) {
}

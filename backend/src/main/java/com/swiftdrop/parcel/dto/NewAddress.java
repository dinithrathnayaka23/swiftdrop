package com.swiftdrop.parcel.dto;

import java.util.UUID;

/**
 * A pickup or drop-off address for {@link NewParcel}. The caller (booking, merchant API) has already
 * validated it.
 *
 * @param addressLine2 optional
 * @param postalCode   optional
 * @param zoneId       {@code delivery_zones.zone_id} found for this address, or {@code null}
 */
public record NewAddress(
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String postalCode,
        UUID zoneId) {
}

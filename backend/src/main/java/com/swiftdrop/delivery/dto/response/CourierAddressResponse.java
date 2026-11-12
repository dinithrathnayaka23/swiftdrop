package com.swiftdrop.delivery.dto.response;

/**
 * A pickup or drop-off stop as the courier sees it: who to meet, how to call them and where.
 * Personal data: only returned to the courier assigned to the parcel.
 */
public record CourierAddressResponse(
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String postalCode) {
}

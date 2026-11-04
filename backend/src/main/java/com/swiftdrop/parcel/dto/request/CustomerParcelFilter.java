package com.swiftdrop.parcel.dto.request;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.LocalDate;

/**
 * Filters of the customer's "My Deliveries" list. Every field is optional.
 *
 * @param search     part of the tracking number, destination city or order reference
 * @param bookedFrom first booking day to include (Sri Lanka time)
 * @param bookedTo   last booking day to include (Sri Lanka time)
 */
public record CustomerParcelFilter(
        String search,
        ParcelStatusCode status,
        LocalDate bookedFrom,
        LocalDate bookedTo) {
}

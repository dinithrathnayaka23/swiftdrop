package com.swiftdrop.parcel.dto.request;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.LocalDate;

/**
 * Filters of the admin Parcels list. Every field is optional.
 *
 * @param search      part of the tracking number, recipient name or merchant name
 * @param area        delivery city
 * @param createdFrom first creation day to include (Sri Lanka time)
 * @param createdTo   last creation day to include (Sri Lanka time)
 */
public record AdminParcelFilter(
        AdminParcelTab tab,
        String search,
        ParcelStatusCode status,
        String area,
        LocalDate createdFrom,
        LocalDate createdTo) {
}

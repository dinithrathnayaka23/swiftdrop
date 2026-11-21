package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;

/**
 * Filters of a merchant's order list, for {@code ParcelService.listByMerchant}. Every field is
 * optional; {@code null} (or a blank search) means "don't filter on this".
 *
 * @param search      part of the tracking number, order reference, recipient name or delivery city,
 *                    ignoring case
 * @param status      current status
 * @param createdFrom first creation time to include (inclusive)
 * @param createdTo   creation time to stop at (exclusive)
 */
public record MerchantParcelFilter(
        String search,
        ParcelStatusCode status,
        Instant createdFrom,
        Instant createdTo) {
}

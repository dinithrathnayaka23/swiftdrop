package com.swiftdrop.merchant.dto.request;

/**
 * The orders list's query parameters as they were sent. {@code MerchantOrderService} checks them,
 * so a bad value gets a 400 that says which parameter is wrong. Every field is optional.
 *
 * @param search part of the tracking number, order reference, recipient name or delivery city
 * @param status a parcel status code, e.g. {@code IN_TRANSIT}
 * @param from   first creation day to include, {@code yyyy-MM-dd} (Sri Lanka time)
 * @param to     last creation day to include, {@code yyyy-MM-dd} (Sri Lanka time)
 */
public record MerchantOrderFilter(String search, String status, String from, String to) {

    /** No filters: every order. */
    public static MerchantOrderFilter none() {
        return new MerchantOrderFilter(null, null, null, null);
    }
}

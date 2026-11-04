package com.swiftdrop.pricing.api;

import java.util.Objects;

/**
 * Where a parcel is picked up or delivered. The pricing module finds the delivery zone itself.
 *
 * @param postalCode optional
 */
public record FeeLocation(String district, String city, String postalCode) {

    public FeeLocation {
        Objects.requireNonNull(district, "district");
        Objects.requireNonNull(city, "city");
    }
}
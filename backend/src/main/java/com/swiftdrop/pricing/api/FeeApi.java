package com.swiftdrop.pricing.api;

/**
 * Public API of the pricing module. Other modules ask for a delivery fee through this interface
 * and never use pricing repositories or entities directly.
 */
public interface FeeApi {

    /** Calculates the delivery fee for a parcel going from the pickup to the drop-off location. */
    FeeEstimate estimateFee(FeeRequest request);
}
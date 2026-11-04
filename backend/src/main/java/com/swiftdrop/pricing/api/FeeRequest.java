package com.swiftdrop.pricing.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What we need to calculate a fee.
 *
 * @param lengthCm optional; {@code widthCm} and {@code heightCm} likewise
 */
public record FeeRequest(
        FeeLocation pickup,
        FeeLocation dropoff,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm) {

    /** Rejects programming errors early; user input is validated by the calling module. */
    public FeeRequest {
        Objects.requireNonNull(pickup, "pickup");
        Objects.requireNonNull(dropoff, "dropoff");
        Objects.requireNonNull(weightKg, "weightKg");
        if (weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg must be greater than 0");
        }
        requirePositiveIfPresent(lengthCm, "lengthCm");
        requirePositiveIfPresent(widthCm, "widthCm");
        requirePositiveIfPresent(heightCm, "heightCm");
    }

    private static void requirePositiveIfPresent(BigDecimal value, String name) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
    }
}
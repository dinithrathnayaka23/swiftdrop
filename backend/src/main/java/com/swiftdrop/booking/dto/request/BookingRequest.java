package com.swiftdrop.booking.dto.request;

import com.swiftdrop.parcel.model.ParcelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A delivery booking, used to get a fee estimate and to confirm the booking.
 *
 * @param lengthCm             optional; {@code widthCm} and {@code heightCm} likewise
 * @param contentDescription   optional
 * @param deliveryInstructions optional
 */
public record BookingRequest(
        @NotNull @Valid BookingAddressRequest pickup,
        @NotNull @Valid BookingAddressRequest dropoff,
        @NotNull ParcelType parcelType,
        @NotNull @Positive @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal lengthCm,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal widthCm,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal heightCm,
        @Size(max = 500) String contentDescription,
        @Size(max = 500) String deliveryInstructions) {
}
package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.parcel.model.ParcelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request submitted by an approved merchant to create a parcel order.
 *
 * <p>Addresses are reused from the booking API because they have the same validated contact and
 * location fields. They are accepted here but are intentionally not exposed by {@code OrderResponse}.
 */
public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String externalOrderReference,
        @NotNull @Valid BookingAddressRequest pickupAddress,
        @NotNull @Valid BookingAddressRequest dropoffAddress,
        @NotNull ParcelType parcelType,
        @NotNull @Positive @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal lengthCm,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal widthCm,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal heightCm,
        @Size(max = 500) String description) {
}

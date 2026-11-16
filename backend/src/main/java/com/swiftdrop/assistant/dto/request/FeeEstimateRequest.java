package com.swiftdrop.assistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of {@code POST /api/v1/assistant/fee-estimate}: the chat's fee form. Same fields as {@code estimate_fee}. */
public record FeeEstimateRequest(
        @NotBlank @Size(max = 60) String pickupDistrict,
        @NotBlank @Size(max = 60) String pickupCity,
        @NotBlank @Size(max = 60) String dropoffDistrict,
        @NotBlank @Size(max = 60) String dropoffCity,
        @NotNull @Positive BigDecimal weightKg,
        @Positive BigDecimal lengthCm,
        @Positive BigDecimal widthCm,
        @Positive BigDecimal heightCm) {
}

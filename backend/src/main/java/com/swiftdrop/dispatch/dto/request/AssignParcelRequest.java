package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Give one parcel to a courier. */
public record AssignParcelRequest(
        @NotNull(message = "Parcel is required") UUID parcelId,
        @NotNull(message = "Courier is required") UUID courierId) {
}

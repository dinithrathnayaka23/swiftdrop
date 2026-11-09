package com.swiftdrop.dispatch.dto.request;

import com.swiftdrop.dispatch.model.CourierAvailability;
import jakarta.validation.constraints.NotNull;

public record ChangeAvailabilityRequest(@NotNull(message = "Availability is required") CourierAvailability availability) {
}

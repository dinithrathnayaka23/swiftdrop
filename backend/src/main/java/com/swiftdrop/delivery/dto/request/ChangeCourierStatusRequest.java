package com.swiftdrop.delivery.dto.request;

import com.swiftdrop.dispatch.api.CourierStatus;
import jakarta.validation.constraints.NotNull;

/** The courier switches themselves Online, On Break or Offline. */
public record ChangeCourierStatusRequest(@NotNull(message = "Status is required") CourierStatus status) {
}

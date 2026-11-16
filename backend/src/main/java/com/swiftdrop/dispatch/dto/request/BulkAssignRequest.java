package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Give several parcels (1-50) to one courier; each parcel succeeds or fails on its own. */
public record BulkAssignRequest(
        @NotNull(message = "Courier is required") UUID courierId,
        @NotEmpty(message = "Choose at least one parcel")
        @Size(max = BulkAssignRequest.MAX_PARCELS, message = "At most " + BulkAssignRequest.MAX_PARCELS
                + " parcels can be assigned at once")
        List<@NotNull(message = "Parcel is required") UUID> parcelIds) {

    public static final int MAX_PARCELS = 50;
}

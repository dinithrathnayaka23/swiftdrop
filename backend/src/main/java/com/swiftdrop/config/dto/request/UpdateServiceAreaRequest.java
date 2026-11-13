package com.swiftdrop.config.dto.request;

import jakarta.validation.constraints.Size;

/** Rename a zone and/or change its description; fields left out stay as they are. Send at least one. */
public record UpdateServiceAreaRequest(
        @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
        String name,
        @Size(max = CreateServiceAreaRequest.MAX_DESCRIPTION, message = "Description must be at most "
                + CreateServiceAreaRequest.MAX_DESCRIPTION + " characters")
        String description) {
}

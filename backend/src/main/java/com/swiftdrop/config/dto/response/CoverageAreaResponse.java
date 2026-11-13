package com.swiftdrop.config.dto.response;

import java.util.UUID;

/** A place a zone covers; {@code postalCode} is {@code null} for the whole city. */
public record CoverageAreaResponse(UUID id, String district, String city, String postalCode, boolean active) {
}

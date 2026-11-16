package com.swiftdrop.config.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A row of the Service Areas list. The summary fields count only active coverage rows.
 *
 * @param cities          distinct, sorted
 * @param districts       distinct, sorted
 * @param postalCodeRange "lowest-highest" postal code (or the one code), {@code null} if none
 * @param coverageCount   active coverage rows
 */
public record ServiceAreaResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<String> cities,
        List<String> districts,
        String postalCodeRange,
        int coverageCount,
        Instant createdAt) {
}

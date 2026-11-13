package com.swiftdrop.config.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One zone: the list row's fields plus every coverage row (active or not). */
public record ServiceAreaDetailResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<String> cities,
        List<String> districts,
        String postalCodeRange,
        int coverageCount,
        Instant createdAt,
        List<CoverageAreaResponse> coverageAreas) {
}

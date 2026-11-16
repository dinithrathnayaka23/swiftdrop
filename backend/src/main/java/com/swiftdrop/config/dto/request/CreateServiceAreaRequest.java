package com.swiftdrop.config.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A new delivery zone ("service area").
 *
 * @param active        defaults to {@code true}
 * @param coverageAreas optional, at most {@value #MAX_COVERAGE_AREAS}
 */
public record CreateServiceAreaRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
        String name,
        @Size(max = MAX_DESCRIPTION, message = "Description must be at most " + MAX_DESCRIPTION + " characters")
        String description,
        Boolean active,
        @Size(max = MAX_COVERAGE_AREAS, message = "At most " + MAX_COVERAGE_AREAS + " coverage areas")
        List<@NotNull(message = "Coverage area is required") @Valid CoverageAreaRequest> coverageAreas) {

    public static final int MAX_COVERAGE_AREAS = 200;
    public static final int MAX_DESCRIPTION = 1000;
}

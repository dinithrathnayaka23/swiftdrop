package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.api.FeeLocation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Temporary resolver used until the zone module publishes its API. It finds no zone, so zone
 * adjustments are skipped. Delete this class when the real resolver is added.
 */
@Component
public class NoZoneResolver implements ZoneResolver {

    @Override
    public Optional<UUID> findZoneId(FeeLocation location) {
        return Optional.empty();
    }
}
package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.api.FeeLocation;
import java.util.Optional;
import java.util.UUID;

/** Finds the delivery zone ({@code delivery_zones.zone_id}) that covers a location. */
public interface ZoneResolver {

    Optional<UUID> findZoneId(FeeLocation location);
}
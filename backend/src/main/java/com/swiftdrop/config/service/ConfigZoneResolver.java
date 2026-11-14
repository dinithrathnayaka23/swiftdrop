package com.swiftdrop.config.service;

import com.swiftdrop.config.api.ZoneApi;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.service.ZoneResolver;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The pricing module's {@link ZoneResolver} (same pattern as dispatch providing the parcel module's
 * {@code DispatchStateLookup}): fee estimates find the pickup and drop-off zones through {@link ZoneApi}.
 */
@Component
@RequiredArgsConstructor
public class ConfigZoneResolver implements ZoneResolver {

    private final ZoneApi zoneApi;

    @Override
    public Optional<UUID> findZoneId(FeeLocation location) {
        return zoneApi.findZoneId(location.district(), location.city(), location.postalCode());
    }
}

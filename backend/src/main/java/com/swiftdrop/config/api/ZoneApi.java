package com.swiftdrop.config.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the config module for delivery zones ({@code delivery_zones.zone_id}). Other modules
 * (pricing, dispatch, booking, ...) use only this interface, never the config repositories or entities.
 *
 * <p>Places are compared after trimming and ignoring case.
 */
public interface ZoneApi {

    /**
     * The active zone covering this place, looking only at active coverage of active zones. Most
     * specific wins: (1) district + city + postal code, when a postal code is given; (2) district +
     * city with no postal code (the whole city). Nothing else is tried.
     *
     * @param postalCode optional
     */
    Optional<UUID> findZoneId(String district, String city, String postalCode);

    /** The ids that are not an active zone (unknown or deactivated); empty if all are fine. */
    Set<UUID> missingOrInactive(Collection<UUID> zoneIds);

    /** Zone names by id, active or not, in one query. Unknown ids are left out. */
    Map<UUID, String> zoneNames(Collection<UUID> zoneIds);
}

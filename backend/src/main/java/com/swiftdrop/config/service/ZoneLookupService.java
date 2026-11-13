package com.swiftdrop.config.service;

import com.swiftdrop.config.api.ZoneApi;
import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import com.swiftdrop.config.repository.ZoneCoverageAreaRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The {@link ZoneApi} other modules use. Read-only. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneLookupService implements ZoneApi {

    private final DeliveryZoneRepository zoneRepository;
    private final ZoneCoverageAreaRepository coverageRepository;

    @Override
    public Optional<UUID> findZoneId(String district, String city, String postalCode) {
        String cleanDistrict = Places.text(district);
        String cleanCity = Places.text(city);
        if (cleanDistrict == null || cleanCity == null) {
            return Optional.empty();
        }
        String cleanPostal = Places.postalCode(postalCode);
        if (cleanPostal != null) {
            Optional<UUID> exact = pick(coverageRepository.findActiveMatches(cleanDistrict, cleanCity, cleanPostal));
            if (exact.isPresent()) {
                return exact;
            }
        }
        return pick(coverageRepository.findActiveCityWideMatches(cleanDistrict, cleanCity));
    }

    @Override
    public Set<UUID> missingOrInactive(Collection<UUID> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> active = zoneRepository.findAllById(Set.copyOf(zoneIds)).stream()
                .filter(DeliveryZone::isActive).map(DeliveryZone::getId).collect(Collectors.toSet());
        Set<UUID> problems = new HashSet<>(zoneIds);
        problems.removeAll(active);
        return problems;
    }

    @Override
    public Map<UUID, String> zoneNames(Collection<UUID> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return Map.of();
        }
        return zoneRepository.findAllById(Set.copyOf(zoneIds)).stream()
                .collect(Collectors.toMap(DeliveryZone::getId, DeliveryZone::getZoneName));
    }

    /**
     * The zone of the matches. The overlap rule allows only one; if older data has more, the zone
     * name decides so the answer is at least stable.
     */
    private static Optional<UUID> pick(List<ZoneCoverageArea> matches) {
        Set<DeliveryZone> zones = matches.stream().map(ZoneCoverageArea::getZone).collect(Collectors.toSet());
        if (zones.size() > 1) {
            log.warn("A place is covered by {} active zones; using the first by name", zones.size());
        }
        return zones.stream().min(Comparator.comparing(DeliveryZone::getZoneName, String.CASE_INSENSITIVE_ORDER))
                .map(DeliveryZone::getId);
    }
}

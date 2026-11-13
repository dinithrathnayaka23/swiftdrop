package com.swiftdrop.config.service;

import static com.swiftdrop.config.repository.ZoneSpecifications.any;
import static com.swiftdrop.config.repository.ZoneSpecifications.isActive;
import static com.swiftdrop.config.repository.ZoneSpecifications.matchesText;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.dto.request.UpdateServiceAreaRequest;
import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.dto.response.ServiceAreaResponse;
import com.swiftdrop.config.mapper.ServiceAreaMapper;
import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import com.swiftdrop.config.repository.ZoneCoverageAreaRepository;
import com.swiftdrop.config.repository.ZoneWriteLock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Delivery zones ("service areas") for the dispatcher: the places each zone covers, and whether it
 * is in use.
 *
 * <p>Rules (names and places are trimmed and compared ignoring case):
 * <ul>
 *   <li>Zone names are unique.</li>
 *   <li>A zone lists each place (district, city, postal code; no postal code = the whole city) once.</li>
 *   <li>A place covered by an active zone can't be covered by another active zone. Checked when a
 *       place is added to an active zone, and again when a zone is activated.</li>
 *   <li>Zones are never deleted (addresses, pricing and couriers point at them): deactivate instead.
 *       Lookups ignore inactive zones.</li>
 * </ul>
 * Every write takes the {@link ZoneWriteLock} first, because neither rule is a database constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceAreaService {

    static final Sort BY_NAME = Sort.by("zoneName").and(Sort.by("id"));
    static final String NAME_TAKEN = "A service area with this name already exists";

    private final DeliveryZoneRepository zoneRepository;
    private final ZoneCoverageAreaRepository coverageRepository;
    private final ZoneWriteLock writeLock;

    // ---- Read --------------------------------------------------------------------------------

    /** Zones sorted by name; {@code search} matches the name or any coverage city, district or postal code. */
    @Transactional(readOnly = true)
    public Page<ServiceAreaResponse> list(String search, Boolean active, int page, int size) {
        Specification<DeliveryZone> spec = any();
        if (StringUtils.hasText(search)) {
            spec = spec.and(matchesText(search));
        }
        if (active != null) {
            spec = spec.and(isActive(active));
        }
        Page<DeliveryZone> found = zoneRepository.findAll(spec, PageRequest.of(page, size, BY_NAME));
        if (found.isEmpty()) {
            return found.map(zone -> ServiceAreaMapper.toRow(zone, List.of()));
        }
        // The coverage of the whole page in one query.
        Map<UUID, List<ZoneCoverageArea>> coverage = coverageRepository
                .findByZone_IdIn(found.getContent().stream().map(DeliveryZone::getId).toList()).stream()
                .collect(Collectors.groupingBy(area -> area.getZone().getId()));
        return found.map(zone -> ServiceAreaMapper.toRow(zone, coverage.getOrDefault(zone.getId(), List.of())));
    }

    /** @throws NotFoundException if there is no such zone */
    @Transactional(readOnly = true)
    public ServiceAreaDetailResponse get(UUID zoneId) {
        DeliveryZone zone = find(zoneId);
        return ServiceAreaMapper.toDetail(zone, coverageRepository.findByZone_Id(zoneId));
    }

    // ---- Zones -------------------------------------------------------------------------------

    /**
     * Adds a zone with its coverage (active unless {@code active} is false).
     *
     * @throws BadRequestException if the name is too short once trimmed
     * @throws ConflictException   if the name is taken, a place is listed twice, or (active zone)
     *                             another active zone already covers one of the places
     */
    @Transactional
    public ServiceAreaDetailResponse create(CreateServiceAreaRequest request) {
        writeLock.acquire();
        String name = validName(request.name());
        if (zoneRepository.existsByZoneNameIgnoreCase(name)) {
            throw ConflictException.forField("name", NAME_TAKEN);
        }
        DeliveryZone zone = new DeliveryZone();
        zone.setZoneName(name);
        zone.setDescription(Places.text(request.description()));
        zone.setActive(request.active() == null || request.active());
        DeliveryZone saved = saveZone(zone);

        List<ZoneCoverageArea> areas = new ArrayList<>();
        Set<Places.Key> seen = new HashSet<>();
        for (CoverageAreaRequest place : request.coverageAreas() == null ? List.<CoverageAreaRequest>of()
                : request.coverageAreas()) {
            ZoneCoverageArea area = newArea(saved, place);
            if (!seen.add(Places.Key.of(area.getDistrict(), area.getCity(), area.getPostalCode()))) {
                throw new ConflictException("%s is listed more than once".formatted(describe(area)));
            }
            areas.add(area);
        }
        List<ZoneCoverageArea> savedAreas = coverageRepository.saveAllAndFlush(areas);
        if (saved.isActive()) {
            refuseOverlaps(saved);
        }
        log.info("Service area {} added with {} coverage areas", saved.getId(), savedAreas.size());
        return ServiceAreaMapper.toDetail(saved, savedAreas);
    }

    /**
     * Renames the zone and/or changes its description; fields left {@code null} stay as they are
     * (a blank description clears it).
     *
     * @throws BadRequestException if nothing is given or the name is too short once trimmed
     * @throws NotFoundException   if there is no such zone
     * @throws ConflictException   if another zone has the name
     */
    @Transactional
    public ServiceAreaDetailResponse update(UUID zoneId, UpdateServiceAreaRequest request) {
        if (request.name() == null && request.description() == null) {
            throw new BadRequestException("Nothing to update");
        }
        writeLock.acquire();
        DeliveryZone zone = find(zoneId);
        if (request.name() != null) {
            String name = validName(request.name());
            if (zoneRepository.existsByZoneNameIgnoreCaseAndIdNot(name, zoneId)) {
                throw ConflictException.forField("name", NAME_TAKEN);
            }
            zone.setZoneName(name);
        }
        if (request.description() != null) {
            zone.setDescription(Places.text(request.description()));
        }
        return ServiceAreaMapper.toDetail(saveZone(zone), coverageRepository.findByZone_Id(zoneId));
    }

    /**
     * Puts the zone back in use, after checking no other active zone covers one of its places.
     *
     * @throws NotFoundException if there is no such zone
     * @throws ConflictException if another active zone covers one of its places
     */
    @Transactional
    public ServiceAreaDetailResponse activate(UUID zoneId) {
        writeLock.acquire();
        DeliveryZone zone = find(zoneId);
        if (!zone.isActive()) {
            zone.setActive(true);
            zoneRepository.saveAndFlush(zone);
            refuseOverlaps(zone);
            log.info("Service area {} activated", zoneId);
        }
        return ServiceAreaMapper.toDetail(zone, coverageRepository.findByZone_Id(zoneId));
    }

    /**
     * Takes the zone out of use (always allowed). Lookups stop finding it; addresses, prices and
     * couriers that point at it keep the id.
     *
     * @throws NotFoundException if there is no such zone
     */
    @Transactional
    public ServiceAreaDetailResponse deactivate(UUID zoneId) {
        writeLock.acquire();
        DeliveryZone zone = find(zoneId);
        if (zone.isActive()) {
            zone.setActive(false);
            log.info("Service area {} deactivated", zoneId);
        }
        return ServiceAreaMapper.toDetail(zone, coverageRepository.findByZone_Id(zoneId));
    }

    // ---- Coverage ----------------------------------------------------------------------------

    /**
     * Adds a place to the zone.
     *
     * @throws NotFoundException if there is no such zone
     * @throws ConflictException if the zone already covers it, or (active zone) another active zone does
     */
    @Transactional
    public CoverageAreaResponse addCoverage(UUID zoneId, CoverageAreaRequest request) {
        writeLock.acquire();
        DeliveryZone zone = find(zoneId);
        ZoneCoverageArea area = newArea(zone, request);
        boolean inZone = area.getPostalCode() == null
                ? coverageRepository.existsCityWideInZone(zoneId, area.getDistrict(), area.getCity())
                : coverageRepository.existsInZone(zoneId, area.getDistrict(), area.getCity(), area.getPostalCode());
        if (inZone) {
            throw new ConflictException("This service area already covers %s".formatted(describe(area)));
        }
        if (zone.isActive()) {
            List<ZoneCoverageArea> taken = area.getPostalCode() == null
                    ? coverageRepository.findActiveCityWideMatches(area.getDistrict(), area.getCity())
                    : coverageRepository.findActiveMatches(area.getDistrict(), area.getCity(), area.getPostalCode());
            taken.stream().filter(other -> !other.getZone().getId().equals(zoneId)).findFirst()
                    .ifPresent(other -> {
                        throw overlap(other);
                    });
        }
        ZoneCoverageArea saved = coverageRepository.saveAndFlush(area);
        return ServiceAreaMapper.toResponse(saved);
    }

    /**
     * Removes a place from the zone. Nothing references coverage rows, so it is deleted.
     *
     * @throws NotFoundException if the zone has no such coverage area
     */
    @Transactional
    public void removeCoverage(UUID zoneId, UUID coverageId) {
        writeLock.acquire();
        ZoneCoverageArea area = coverageRepository.findById(coverageId)
                .filter(found -> found.getZone().getId().equals(zoneId))
                .orElseThrow(() -> new NotFoundException("Coverage area not found"));
        coverageRepository.delete(area);
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private DeliveryZone find(UUID zoneId) {
        return zoneRepository.findById(zoneId).orElseThrow(() -> new NotFoundException("Service area not found"));
    }

    private DeliveryZone saveZone(DeliveryZone zone) {
        try {
            return zoneRepository.saveAndFlush(zone);
        } catch (DataIntegrityViolationException ex) {
            // ux_delivery_zones_zone_name (exact case); the lock makes this a programming or data error.
            throw ConflictException.forField("name", NAME_TAKEN);
        }
    }

    /** The trimmed name, 2-100 characters. */
    static String validName(String name) {
        String cleaned = Places.text(name);
        if (cleaned == null || cleaned.length() < 2 || cleaned.length() > 100) {
            throw new BadRequestException("Name must be 2-100 characters");
        }
        return cleaned;
    }

    private static ZoneCoverageArea newArea(DeliveryZone zone, CoverageAreaRequest place) {
        String district = Places.text(place.district());
        String city = Places.text(place.city());
        if (district == null || city == null) {
            throw new BadRequestException("District and city are required");
        }
        ZoneCoverageArea area = new ZoneCoverageArea();
        area.setZone(zone);
        area.setDistrict(district);
        area.setCity(city);
        area.setPostalCode(Places.postalCode(place.postalCode()));
        area.setActive(true);
        return area;
    }

    /** @throws ConflictException naming the other zone if one of this zone's active places is taken */
    private void refuseOverlaps(DeliveryZone zone) {
        coverageRepository.findOverlapsWithOtherActiveZones(zone.getId()).stream().findFirst().ifPresent(other -> {
            throw overlap(other);
        });
    }

    private static ConflictException overlap(ZoneCoverageArea other) {
        return new ConflictException("%s is already covered by the active service area \"%s\""
                .formatted(describe(other), other.getZone().getZoneName()));
    }

    /** "City, District" or "City, District (postal code)"; place names, not personal data. */
    static String describe(ZoneCoverageArea area) {
        String place = area.getCity() + ", " + area.getDistrict();
        return area.getPostalCode() == null ? place : place + " (" + area.getPostalCode() + ")";
    }
}

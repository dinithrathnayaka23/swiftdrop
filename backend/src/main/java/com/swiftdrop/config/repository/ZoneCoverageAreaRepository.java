package com.swiftdrop.config.repository;

import com.swiftdrop.config.model.ZoneCoverageArea;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Private to the config module. District, city and postal code are compared ignoring case; a
 * {@code null} postal code is a value of its own ("the whole city").
 */
public interface ZoneCoverageAreaRepository extends JpaRepository<ZoneCoverageArea, UUID> {

    /** The coverage of these zones (a page of the list) in one query. */
    List<ZoneCoverageArea> findByZone_IdIn(Collection<UUID> zoneIds);

    List<ZoneCoverageArea> findByZone_Id(UUID zoneId);

    // ---- Lookups: active rows of active zones ------------------------------------------------

    /** Exact matches with this postal code. */
    @Query("""
            select c from ZoneCoverageArea c join fetch c.zone z
            where c.active = true and z.active = true
              and upper(c.district) = upper(:district) and upper(c.city) = upper(:city)
              and upper(c.postalCode) = upper(:postalCode)
            """)
    List<ZoneCoverageArea> findActiveMatches(@Param("district") String district, @Param("city") String city,
                                             @Param("postalCode") String postalCode);

    /** City-wide matches (rows without a postal code). */
    @Query("""
            select c from ZoneCoverageArea c join fetch c.zone z
            where c.active = true and z.active = true
              and upper(c.district) = upper(:district) and upper(c.city) = upper(:city)
              and c.postalCode is null
            """)
    List<ZoneCoverageArea> findActiveCityWideMatches(@Param("district") String district, @Param("city") String city);

    // ---- Overlap rule ------------------------------------------------------------------------

    /** Whether the zone already has an active row for this place with this postal code. */
    @Query("""
            select count(c) > 0 from ZoneCoverageArea c
            where c.zone.id = :zoneId and c.active = true
              and upper(c.district) = upper(:district) and upper(c.city) = upper(:city)
              and upper(c.postalCode) = upper(:postalCode)
            """)
    boolean existsInZone(@Param("zoneId") UUID zoneId, @Param("district") String district,
                         @Param("city") String city, @Param("postalCode") String postalCode);

    /** Whether the zone already has an active city-wide row for this place. */
    @Query("""
            select count(c) > 0 from ZoneCoverageArea c
            where c.zone.id = :zoneId and c.active = true
              and upper(c.district) = upper(:district) and upper(c.city) = upper(:city)
              and c.postalCode is null
            """)
    boolean existsCityWideInZone(@Param("zoneId") UUID zoneId, @Param("district") String district,
                                 @Param("city") String city);

    /**
     * Active rows of <em>other</em> active zones that cover the same place (district, city, postal
     * code or both without one) as an active row of this zone. Empty means the zone may be active.
     */
    @Query("""
            select other from ZoneCoverageArea other join fetch other.zone otherZone, ZoneCoverageArea mine
            where mine.zone.id = :zoneId and mine.active = true
              and other.active = true and otherZone.active = true and otherZone.id <> :zoneId
              and upper(other.district) = upper(mine.district) and upper(other.city) = upper(mine.city)
              and (upper(other.postalCode) = upper(mine.postalCode)
                   or (other.postalCode is null and mine.postalCode is null))
            """)
    List<ZoneCoverageArea> findOverlapsWithOtherActiveZones(@Param("zoneId") UUID zoneId);
}

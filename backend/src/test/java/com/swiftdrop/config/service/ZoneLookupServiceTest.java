package com.swiftdrop.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import com.swiftdrop.config.repository.ZoneCoverageAreaRepository;
import com.swiftdrop.pricing.api.FeeLocation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZoneLookupServiceTest {

    @Mock private DeliveryZoneRepository zoneRepository;
    @Mock private ZoneCoverageAreaRepository coverageRepository;
    @InjectMocks private ZoneLookupService service;

    private static DeliveryZone zone(String name, boolean active) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(UUID.randomUUID());
        zone.setZoneName(name);
        zone.setActive(active);
        return zone;
    }

    private static ZoneCoverageArea row(DeliveryZone zone) {
        ZoneCoverageArea area = new ZoneCoverageArea();
        area.setZone(zone);
        return area;
    }

    @Test
    void anExactPostalCodeMatchWinsOverTheWholeCity() {
        DeliveryZone exact = zone("Colombo 03", true);
        when(coverageRepository.findActiveMatches("Colombo", "Kollupitiya", "00300")).thenReturn(List.of(row(exact)));

        assertThat(service.findZoneId("Colombo", "Kollupitiya", "00300")).contains(exact.getId());
        verify(coverageRepository, never()).findActiveCityWideMatches(any(), any());
    }

    @Test
    void withoutAnExactMatchTheWholeCityRowIsUsed() {
        DeliveryZone cityWide = zone("Colombo 02-07", true);
        when(coverageRepository.findActiveMatches("Colombo", "Kollupitiya", "00399")).thenReturn(List.of());
        when(coverageRepository.findActiveCityWideMatches("Colombo", "Kollupitiya")).thenReturn(List.of(row(cityWide)));

        assertThat(service.findZoneId("Colombo", "Kollupitiya", "00399")).contains(cityWide.getId());
    }

    @Test
    void withoutAPostalCodeOnlyTheWholeCityIsTried() {
        when(coverageRepository.findActiveCityWideMatches("Kandy", "Kandy")).thenReturn(List.of());

        assertThat(service.findZoneId("Kandy", "Kandy", "  ")).isEmpty();
        verify(coverageRepository, never()).findActiveMatches(any(), any(), any());
    }

    @Test
    void placesAreTrimmedAndPostalCodesUpperCased() {
        DeliveryZone zone = zone("Galle", true);
        when(coverageRepository.findActiveMatches("Galle", "Galle Fort", "GF-80000")).thenReturn(List.of(row(zone)));

        assertThat(service.findZoneId("  Galle ", " Galle   Fort ", " gf-80000 ")).contains(zone.getId());
    }

    @Test
    void aBlankDistrictOrCityFindsNothingWithoutQuerying() {
        assertThat(service.findZoneId(" ", "Kandy", null)).isEmpty();
        assertThat(service.findZoneId("Kandy", null, null)).isEmpty();
        verifyNoInteractions(coverageRepository);
    }

    @Test
    void ifOldDataHasTwoMatchesTheZoneNameDecides() {
        DeliveryZone later = zone("Western", true);
        DeliveryZone first = zone("colombo", true);
        when(coverageRepository.findActiveCityWideMatches("Colombo", "Fort"))
                .thenReturn(List.of(row(later), row(first)));

        assertThat(service.findZoneId("Colombo", "Fort", null)).contains(first.getId());
    }

    @Test
    void reportsUnknownAndInactiveZones() {
        DeliveryZone active = zone("Kandy", true);
        DeliveryZone inactive = zone("Old", false);
        UUID unknown = UUID.randomUUID();
        when(zoneRepository.findAllById(Set.of(active.getId(), inactive.getId(), unknown)))
                .thenReturn(List.of(active, inactive));

        assertThat(service.missingOrInactive(List.of(active.getId(), inactive.getId(), unknown)))
                .containsExactlyInAnyOrder(inactive.getId(), unknown);
        assertThat(service.missingOrInactive(List.of())).isEmpty();
    }

    @Test
    void namesZonesActiveOrNotInOneQuery() {
        DeliveryZone active = zone("Kandy", true);
        DeliveryZone inactive = zone("Old", false);
        when(zoneRepository.findAllById(Set.of(active.getId(), inactive.getId()))).thenReturn(List.of(active, inactive));

        assertThat(service.zoneNames(List.of(active.getId(), inactive.getId())))
                .isEqualTo(Map.of(active.getId(), "Kandy", inactive.getId(), "Old"));
        assertThat(service.zoneNames(null)).isEmpty();
    }

    @Test
    void thePricingResolverAsksZoneApi() {
        UUID zoneId = UUID.randomUUID();
        ZoneLookupService api = org.mockito.Mockito.mock(ZoneLookupService.class);
        when(api.findZoneId("Kandy", "Kandy", "20000")).thenReturn(Optional.of(zoneId));

        assertThat(new ConfigZoneResolver(api).findZoneId(new FeeLocation("Kandy", "Kandy", "20000")))
                .contains(zoneId);
    }
}

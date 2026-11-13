package com.swiftdrop.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;

@ExtendWith(MockitoExtension.class)
class DevServiceAreaSeederTest {

    @Mock private DeliveryZoneRepository zoneRepository;
    @Mock private ServiceAreaService serviceAreaService;
    @InjectMocks private DevServiceAreaSeeder seeder;

    @Test
    void runsOnlyInTheDevProfile() {
        assertThat(DevServiceAreaSeeder.class.getAnnotation(Profile.class).value()).containsExactly("dev");
    }

    @Test
    void seedsEveryZoneWhenThereAreNone() {
        when(zoneRepository.count()).thenReturn(0L);

        seeder.run(null);

        verify(serviceAreaService, times(DevServiceAreaSeeder.ZONES.size())).create(any());
    }

    @Test
    void leavesExistingZonesAlone() {
        when(zoneRepository.count()).thenReturn(3L);

        seeder.run(null);

        verify(serviceAreaService, never()).create(any());
    }

    @Test
    void theDemoZonesAreValidAndNeverCoverAPlaceTwice() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<Places.Key> places = new HashSet<>();
        for (CreateServiceAreaRequest zone : DevServiceAreaSeeder.ZONES) {
            assertThat(validator.validate(zone)).as(zone.name()).isEmpty();
            for (CoverageAreaRequest place : zone.coverageAreas()) {
                assertThat(places.add(Places.Key.of(place.district(), place.city(), place.postalCode())))
                        .as(place.toString()).isTrue();
            }
        }
        assertThat(DevServiceAreaSeeder.ZONES).hasSizeBetween(7, 9);
        assertThat(DevServiceAreaSeeder.ZONES.stream().map(CreateServiceAreaRequest::name).distinct().toList())
                .hasSameSizeAs(List.copyOf(DevServiceAreaSeeder.ZONES));
    }
}

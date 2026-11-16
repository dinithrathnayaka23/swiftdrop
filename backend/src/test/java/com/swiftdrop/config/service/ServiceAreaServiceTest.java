package com.swiftdrop.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.dto.request.UpdateServiceAreaRequest;
import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.dto.response.ServiceAreaResponse;
import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import com.swiftdrop.config.repository.ZoneCoverageAreaRepository;
import com.swiftdrop.config.repository.ZoneWriteLock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ServiceAreaServiceTest {

    private static final UUID ZONE_ID = UUID.randomUUID();
    private static final UUID OTHER_ZONE_ID = UUID.randomUUID();

    @Mock private DeliveryZoneRepository zoneRepository;
    @Mock private ZoneCoverageAreaRepository coverageRepository;
    @Mock private ZoneWriteLock writeLock;
    @InjectMocks private ServiceAreaService service;

    // ---- Helpers -----------------------------------------------------------------------------

    private static DeliveryZone zone(UUID id, String name, boolean active) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(id);
        zone.setZoneName(name);
        zone.setActive(active);
        zone.setCreatedAt(Instant.now());
        return zone;
    }

    private static ZoneCoverageArea area(DeliveryZone zone, String district, String city, String postalCode) {
        ZoneCoverageArea area = new ZoneCoverageArea();
        area.setId(UUID.randomUUID());
        area.setZone(zone);
        area.setDistrict(district);
        area.setCity(city);
        area.setPostalCode(postalCode);
        area.setActive(true);
        return area;
    }

    private void zoneExists(DeliveryZone zone) {
        when(zoneRepository.findById(zone.getId())).thenReturn(Optional.of(zone));
    }

    /** saveAndFlush gives new zones an id, like the database. */
    private void zonesSave() {
        when(zoneRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            DeliveryZone zone = invocation.getArgument(0);
            if (zone.getId() == null) {
                zone.setId(ZONE_ID);
            }
            return zone;
        });
    }

    private void coverageSaves() {
        when(coverageRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static CreateServiceAreaRequest create(String name, Boolean active, CoverageAreaRequest... areas) {
        return new CreateServiceAreaRequest(name, "  Central business district ", active, List.of(areas));
    }

    // ---- Create ------------------------------------------------------------------------------

    @Test
    void createTakesTheLockFirstAndStoresTrimmedValues() {
        zonesSave();
        coverageSaves();

        ServiceAreaDetailResponse created = service.create(create("  Colombo   01 ", null,
                new CoverageAreaRequest(" Colombo ", "  Fort ", " 00100 "),
                new CoverageAreaRequest("Colombo", "Pettah", "00110 ")));

        InOrder order = inOrder(writeLock, zoneRepository);
        order.verify(writeLock).acquire();
        order.verify(zoneRepository).existsByZoneNameIgnoreCase("Colombo 01");
        assertThat(created.name()).isEqualTo("Colombo 01");
        assertThat(created.description()).isEqualTo("Central business district");
        assertThat(created.active()).isTrue(); // default
        assertThat(created.coverageAreas()).extracting(CoverageAreaResponse::city).containsExactly("Fort", "Pettah");
        assertThat(created.postalCodeRange()).isEqualTo("00100-00110");
        verify(coverageRepository).findOverlapsWithOtherActiveZones(ZONE_ID);
    }

    @Test
    void zoneNamesAreUniqueIgnoringCase() {
        when(zoneRepository.existsByZoneNameIgnoreCase("kandy")).thenReturn(true);

        assertThatThrownBy(() -> service.create(create("kandy", true)))
                .isInstanceOf(ConflictException.class).hasMessage(ServiceAreaService.NAME_TAKEN)
                .satisfies(ex -> assertThat(((ConflictException) ex).getFieldErrors())
                        .extracting(error -> error.field()).containsExactly("name"));
        verify(zoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void aNameTooShortOnceTrimmedIsRefused() {
        assertThatThrownBy(() -> service.create(create("  K  ", true))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ServiceAreaService.validName("x".repeat(101)))
                .isInstanceOf(BadRequestException.class);
        assertThat(ServiceAreaService.validName(" Galle  Fort ")).isEqualTo("Galle Fort");
    }

    @Test
    void aZoneListsEachPlaceOnceIgnoringCaseAndSpaces() {
        zonesSave();

        assertThatThrownBy(() -> service.create(create("Kandy", true,
                new CoverageAreaRequest("Kandy", "Peradeniya", null),
                new CoverageAreaRequest(" KANDY", "peradeniya  ", null))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("listed more than once");
        verify(coverageRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void anActiveZoneCannotCoverAPlaceAnotherActiveZoneCovers() {
        zonesSave();
        coverageSaves();
        DeliveryZone other = zone(OTHER_ZONE_ID, "Colombo 02-07", true);
        when(coverageRepository.findOverlapsWithOtherActiveZones(ZONE_ID))
                .thenReturn(List.of(area(other, "Colombo", "Kollupitiya", "00300")));

        assertThatThrownBy(() -> service.create(create("Colombo South", true,
                new CoverageAreaRequest("Colombo", "Kollupitiya", "00300"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kollupitiya, Colombo (00300) is already covered by the active service area \"Colombo 02-07\"");
    }

    @Test
    void anInactiveZoneIsNotCheckedForOverlapsUntilItIsActivated() {
        zonesSave();
        coverageSaves();

        ServiceAreaDetailResponse created = service.create(create("Draft zone", false,
                new CoverageAreaRequest("Colombo", "Kollupitiya", "00300")));

        assertThat(created.active()).isFalse();
        verify(coverageRepository, never()).findOverlapsWithOtherActiveZones(any());
    }

    // ---- Update, activate, deactivate --------------------------------------------------------

    @Test
    void updateNeedsAFieldAndKeepsNamesUnique() {
        assertThatThrownBy(() -> service.update(ZONE_ID, new UpdateServiceAreaRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(writeLock);

        zoneExists(zone(ZONE_ID, "Kandy", true));
        when(zoneRepository.existsByZoneNameIgnoreCaseAndIdNot("GALLE", ZONE_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.update(ZONE_ID, new UpdateServiceAreaRequest(" GALLE ", null)))
                .isInstanceOf(ConflictException.class).hasMessage(ServiceAreaService.NAME_TAKEN);
    }

    @Test
    void updateRenamesAndABlankDescriptionClearsIt() {
        DeliveryZone zone = zone(ZONE_ID, "Kandy", true);
        zone.setDescription("Old");
        zoneExists(zone);
        zonesSave();

        ServiceAreaDetailResponse updated = service.update(ZONE_ID, new UpdateServiceAreaRequest("Kandy City", " "));

        verify(writeLock).acquire();
        assertThat(updated.name()).isEqualTo("Kandy City");
        assertThat(updated.description()).isNull();
    }

    @Test
    void activatingReChecksTheOverlapRule() {
        DeliveryZone zone = zone(ZONE_ID, "Galle", false);
        zoneExists(zone);
        DeliveryZone other = zone(OTHER_ZONE_ID, "Southern", true);
        when(coverageRepository.findOverlapsWithOtherActiveZones(ZONE_ID))
                .thenReturn(List.of(area(other, "Galle", "Galle", null)));

        assertThatThrownBy(() -> service.activate(ZONE_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("\"Southern\"");
        verify(writeLock).acquire();
        verify(zoneRepository).saveAndFlush(zone); // flushed before the check; the 409 rolls it back
    }

    @Test
    void activatingAZoneWithoutOverlapsPutsItInUse() {
        DeliveryZone zone = zone(ZONE_ID, "Galle", false);
        zoneExists(zone);

        assertThat(service.activate(ZONE_ID).active()).isTrue();
        verify(coverageRepository).findOverlapsWithOtherActiveZones(ZONE_ID);
    }

    @Test
    void activatingAnActiveZoneChangesNothing() {
        zoneExists(zone(ZONE_ID, "Galle", true));

        service.activate(ZONE_ID);

        verify(zoneRepository, never()).saveAndFlush(any());
        verify(coverageRepository, never()).findOverlapsWithOtherActiveZones(any());
    }

    @Test
    void deactivatingIsAlwaysAllowed() {
        DeliveryZone zone = zone(ZONE_ID, "Galle", true);
        zoneExists(zone);

        assertThat(service.deactivate(ZONE_ID).active()).isFalse();
        verify(writeLock).acquire();
        verify(coverageRepository, never()).findOverlapsWithOtherActiveZones(any());
    }

    @Test
    void anUnknownZoneIsNotFound() {
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ZONE_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deactivate(ZONE_ID)).isInstanceOf(NotFoundException.class);
    }

    // ---- Coverage ----------------------------------------------------------------------------

    @Test
    void addsANormalisedPlaceToAnActiveZone() {
        DeliveryZone zone = zone(ZONE_ID, "Kandy", true);
        zoneExists(zone);
        when(coverageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CoverageAreaResponse added = service.addCoverage(ZONE_ID, new CoverageAreaRequest(" Kandy ", " Katugastota", " 20800 "));

        assertThat(added.city()).isEqualTo("Katugastota");
        assertThat(added.postalCode()).isEqualTo("20800");
        verify(coverageRepository).existsInZone(ZONE_ID, "Kandy", "Katugastota", "20800");
        verify(coverageRepository).findActiveMatches("Kandy", "Katugastota", "20800");
    }

    @Test
    void aPlaceTheZoneAlreadyCoversIsRefused() {
        zoneExists(zone(ZONE_ID, "Kandy", true));
        when(coverageRepository.existsCityWideInZone(ZONE_ID, "Kandy", "Peradeniya")).thenReturn(true);

        assertThatThrownBy(() -> service.addCoverage(ZONE_ID, new CoverageAreaRequest("Kandy", "Peradeniya", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already covers Peradeniya, Kandy");
        verify(coverageRepository, never()).saveAndFlush(any());
    }

    @Test
    void aPlaceAnotherActiveZoneCoversIsRefusedNamingThatZone() {
        DeliveryZone zone = zone(ZONE_ID, "Kandy", true);
        zoneExists(zone);
        when(coverageRepository.findActiveCityWideMatches("Kandy", "Peradeniya"))
                .thenReturn(List.of(area(zone(OTHER_ZONE_ID, "Hill Country", true), "Kandy", "Peradeniya", null)));

        assertThatThrownBy(() -> service.addCoverage(ZONE_ID, new CoverageAreaRequest("Kandy", "Peradeniya", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("\"Hill Country\"");
    }

    @Test
    void anInactiveZoneMayCoverAPlaceAnActiveZoneCovers() {
        zoneExists(zone(ZONE_ID, "Kandy (old)", false));
        when(coverageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addCoverage(ZONE_ID, new CoverageAreaRequest("Kandy", "Peradeniya", null));

        verify(coverageRepository, never()).findActiveCityWideMatches(any(), any());
    }

    @Test
    void removesOnlyACoverageAreaOfThatZone() {
        DeliveryZone zone = zone(ZONE_ID, "Kandy", true);
        ZoneCoverageArea mine = area(zone, "Kandy", "Peradeniya", null);
        ZoneCoverageArea elsewhere = area(zone(OTHER_ZONE_ID, "Galle", true), "Galle", "Galle", null);
        when(coverageRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(coverageRepository.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        service.removeCoverage(ZONE_ID, mine.getId());
        assertThatThrownBy(() -> service.removeCoverage(ZONE_ID, elsewhere.getId()))
                .isInstanceOf(NotFoundException.class);

        verify(coverageRepository).delete(mine);
        verify(coverageRepository, never()).delete(elsewhere);
        verify(writeLock, times(2)).acquire();
    }

    // ---- List --------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void listsZonesByNameLoadingThePagesCoverageInOneQuery() {
        DeliveryZone colombo = zone(ZONE_ID, "Colombo 01", true);
        DeliveryZone kandy = zone(OTHER_ZONE_ID, "Kandy", true);
        when(zoneRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(colombo, kandy)));
        when(coverageRepository.findByZone_IdIn(List.of(ZONE_ID, OTHER_ZONE_ID))).thenReturn(List.of(
                area(colombo, "Colombo", "Fort", "00100"), area(kandy, "Kandy", "Kandy", null)));

        Page<ServiceAreaResponse> rows = service.list("col", true, 0, 20);

        assertThat(rows.getContent()).extracting(ServiceAreaResponse::coverageCount).containsExactly(1, 1);
        assertThat(rows.getContent().get(0).postalCodeRange()).isEqualTo("00100");
        assertThat(rows.getContent().get(1).postalCodeRange()).isNull();
        verify(coverageRepository, times(1)).findByZone_IdIn(any());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getSort()).isEqualTo(ServiceAreaService.BY_NAME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyPageNeedsNoCoverageQuery() {
        when(zoneRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.list(null, null, 0, 20)).isEmpty();
        verifyNoInteractions(coverageRepository);
    }
}

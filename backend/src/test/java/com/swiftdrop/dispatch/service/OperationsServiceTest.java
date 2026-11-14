package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.dto.response.ActiveDeliveryResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.dto.response.OverviewResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.CourierDayLoad;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OperationsServiceTest {

    /** Local midnight of 14 Nov 2026 in Sri Lanka (UTC+5:30). */
    private static final Instant MIDNIGHT = Instant.parse("2026-11-13T18:30:00Z");
    private static final Instant NOON = MIDNIGHT.plusSeconds(12 * 3600);

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private ParcelService parcelService;
    @Mock private UserApi userApi;
    @Mock private CourierService courierService;
    @Mock private SystemSettingsService settingsService;
    @InjectMocks private OperationsService service;

    // ---- Helpers -----------------------------------------------------------------------------

    private static ParcelDetails parcel(UUID id, ParcelStatusCode status) {
        AddressDetails address = new AddressDetails(UUID.randomUUID(), "Kasun Perera", "+94771234567", "12 Galle Road",
                null, "Kandy", "Central", null, null);
        return new ParcelDetails(id, "SD-" + id.toString().substring(0, 8), UUID.randomUUID(), null, null, address,
                address, ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, status, null, BigDecimal.TEN,
                "LKR", NOON, NOON);
    }

    private static Courier courier() {
        Courier courier = new Courier();
        courier.setId(UUID.randomUUID());
        courier.setUserId(UUID.randomUUID());
        return courier;
    }

    private static DeliveryAssignment inProgress(UUID parcelId, Courier courier) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setParcelId(parcelId);
        assignment.setCourier(courier);
        assignment.setAssignmentStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setAssignedAt(NOON);
        return assignment;
    }

    private static UserSummary account(UUID id, String name) {
        return new UserSummary(id, name, "c@example.com", "+94771234567", Role.COURIER, AccountStatus.ACTIVE, true,
                NOON);
    }

    private static Map<ParcelStatusCode, Long> counts(long created, long pickedUp, long inTransit, long out,
                                                       long delivered, long attempts, long failed) {
        Map<ParcelStatusCode, Long> counts = new EnumMap<>(ParcelStatusCode.class);
        counts.put(ParcelStatusCode.CREATED, created);
        counts.put(ParcelStatusCode.PICKED_UP, pickedUp);
        counts.put(ParcelStatusCode.IN_TRANSIT, inTransit);
        counts.put(ParcelStatusCode.OUT_FOR_DELIVERY, out);
        counts.put(ParcelStatusCode.DELIVERED, delivered);
        counts.put(ParcelStatusCode.FAILED_ATTEMPT, attempts);
        counts.put(ParcelStatusCode.FAILED, failed);
        return counts;
    }

    /** Everything the overview reads, with these parcel counts and open ASSIGNED assignments. */
    private void overviewData(Map<ParcelStatusCode, Long> byStatus, long openAssigned) {
        when(parcelService.countByStatus()).thenReturn(byStatus);
        when(assignmentRepository.countByEndedAtIsNullAndAssignmentStatus(AssignmentStatus.ASSIGNED))
                .thenReturn(openAssigned);
        when(courierService.summary()).thenReturn(new CourierSummaryResponse(10, 6, 1, 3));
        SystemSettingsView settings = mock(SystemSettingsView.class);
        when(settings.failedDeliveryAction()).thenReturn(FailedDeliveryAction.RETRY_DELIVERY);
        when(settingsService.current()).thenReturn(settings);
    }

    // ---- Active deliveries -------------------------------------------------------------------

    @Test
    void activeDeliveriesAreOpenInProgressAssignmentsNewestFirstWithTheirParcels() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Courier courier = courier();
        when(assignmentRepository.findByEndedAtIsNullAndAssignmentStatus(eq(AssignmentStatus.IN_PROGRESS), any()))
                .thenReturn(new PageImpl<>(List.of(inProgress(first, courier), inProgress(second, courier))));
        when(parcelService.findAllByIds(List.of(first, second))).thenReturn(List.of(
                parcel(first, ParcelStatusCode.OUT_FOR_DELIVERY), parcel(second, ParcelStatusCode.PICKED_UP)));
        when(userApi.findUser(courier.getUserId())).thenReturn(Optional.of(account(courier.getUserId(), "Ramesh")));

        List<ActiveDeliveryResponse> rows = service.activeDeliveries(2, 25).getContent();

        assertThat(rows).extracting(ActiveDeliveryResponse::parcelStatus)
                .containsExactly(ParcelStatusCode.OUT_FOR_DELIVERY, ParcelStatusCode.PICKED_UP);
        assertThat(rows.get(0).courierName()).isEqualTo("Ramesh");
        assertThat(rows.get(0).destinationCity()).isEqualTo("Kandy");
        assertThat(rows.get(0).assignedAt()).isEqualTo(NOON);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findByEndedAtIsNullAndAssignmentStatus(any(), page.capture());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(2, 25, OperationsService.NEWEST_FIRST));
        verify(parcelService, times(1)).findAllByIds(any());
        verify(userApi, times(1)).findUser(any()); // one courier on the page, read once
    }

    @Test
    void noActiveDeliveriesNeedNoOtherCalls() {
        when(assignmentRepository.findByEndedAtIsNullAndAssignmentStatus(any(), any())).thenReturn(Page.empty());

        assertThat(service.activeDeliveries(0, 20)).isEmpty();
        verifyNoInteractions(parcelService, userApi);
    }

    // ---- Overview: definitions ---------------------------------------------------------------

    @Test
    void theKpisAndTodaysStatusFollowTheDocumentedDefinitions() {
        overviewData(counts(7, 2, 3, 4, 50, 5, 9), 3);
        when(assignmentRepository.countByAssignmentStatusAndEndedAtGreaterThanEqual(AssignmentStatus.COMPLETED,
                MIDNIGHT)).thenReturn(12L);
        when(assignmentRepository.countByAssignmentStatusAndEndedAtGreaterThanEqual(AssignmentStatus.FAILED,
                MIDNIGHT)).thenReturn(2L);

        OverviewResponse overview = service.overview(NOON);

        OverviewResponse.Kpis kpis = overview.kpis();
        assertThat(kpis.unassignedParcels()).isEqualTo(4);   // CREATED 7 - open ASSIGNED 3
        assertThat(kpis.activeDeliveries()).isEqualTo(9);    // 2 + 3 + 4
        assertThat(kpis.couriersOnline()).isEqualTo(6);
        assertThat(kpis.couriersOnBreak()).isEqualTo(1);
        assertThat(kpis.couriersTotal()).isEqualTo(10);
        assertThat(kpis.completedToday()).isEqualTo(12);
        assertThat(kpis.failedToday()).isEqualTo(2);
        assertThat(kpis.needsDecision()).isEqualTo(5);       // FAILED_ATTEMPT parcels
        assertThat(overview.todayStatus()).isEqualTo(new OverviewResponse.TodayStatus(12, 5, 4, 2));
        assertThat(overview.defaultAction()).isEqualTo(FailedDeliveryAction.RETRY_DELIVERY);
        assertThat(overview.date()).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void unassignedNeverGoesBelowZero() {
        overviewData(counts(2, 0, 0, 0, 0, 0, 0), 5);

        assertThat(service.overview(NOON).kpis().unassignedParcels()).isZero();
    }

    @Test
    void todayStartsAtSriLankanMidnight() {
        overviewData(counts(0, 0, 0, 0, 0, 0, 0), 0);

        OverviewResponse atMidnight = service.overview(MIDNIGHT);
        verify(assignmentRepository).countByAssignmentStatusAndEndedAtGreaterThanEqual(AssignmentStatus.COMPLETED,
                MIDNIGHT);
        verify(assignmentRepository).findBusiestActiveCouriers(eq(MIDNIGHT), any());
        assertThat(atMidnight.date()).isEqualTo(LocalDate.of(2026, 11, 14));

        OverviewResponse justBefore = service.overview(MIDNIGHT.minusSeconds(1));
        Instant yesterday = MIDNIGHT.minusSeconds(86_400);
        verify(assignmentRepository).countByAssignmentStatusAndEndedAtGreaterThanEqual(AssignmentStatus.FAILED,
                yesterday);
        assertThat(justBefore.date()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    // ---- Overview: courier workload ----------------------------------------------------------

    @Test
    void theWorkloadShowsUpToFiveCouriersBusiestFirstThenByName() {
        overviewData(counts(0, 0, 0, 0, 0, 0, 0), 0);
        CourierDayLoad zara = new CourierDayLoad(UUID.randomUUID(), UUID.randomUUID(), CourierAvailability.ONLINE,
                1, 1, 2);
        CourierDayLoad amal = new CourierDayLoad(UUID.randomUUID(), UUID.randomUUID(), CourierAvailability.ON_BREAK,
                0, 0, 4);
        CourierDayLoad idle = new CourierDayLoad(UUID.randomUUID(), UUID.randomUUID(), CourierAvailability.OFFLINE,
                0, 0, 1);
        when(assignmentRepository.findBusiestActiveCouriers(eq(MIDNIGHT), any())).thenReturn(List.of(zara, amal, idle));
        when(userApi.findUser(zara.userId())).thenReturn(Optional.of(account(zara.userId(), "Zara")));
        when(userApi.findUser(amal.userId())).thenReturn(Optional.of(account(amal.userId(), "Amal")));
        when(userApi.findUser(idle.userId())).thenReturn(Optional.of(account(idle.userId(), "Idle")));

        List<OverviewResponse.CourierLoad> rows = service.overview(NOON).courierWorkload();

        // Zara and Amal both have 4 today: the name decides among the ones shown.
        assertThat(rows).extracting(OverviewResponse.CourierLoad::name).containsExactly("Amal", "Zara", "Idle");
        OverviewResponse.CourierLoad zaraRow = rows.get(1);
        assertThat(zaraRow.todaysTotal()).isEqualTo(4);
        assertThat(zaraRow.completed()).isEqualTo(2);
        assertThat(zaraRow.inProgress()).isEqualTo(1);
        assertThat(zaraRow.pending()).isEqualTo(1);
        assertThat(zaraRow.availability()).isEqualTo(CourierAvailability.ONLINE);
        ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findBusiestActiveCouriers(any(), limit.capture());
        assertThat(limit.getValue().getPageSize()).isEqualTo(OperationsService.WORKLOAD_SIZE).isEqualTo(5);
        verify(userApi, times(3)).findUser(any());
    }
}

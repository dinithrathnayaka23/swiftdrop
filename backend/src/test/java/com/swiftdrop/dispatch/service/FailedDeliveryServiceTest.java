package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.dto.request.FailedDeliveryScope;
import com.swiftdrop.dispatch.dto.response.FailedDecisionResponse;
import com.swiftdrop.dispatch.dto.response.FailedDeliveryResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FailedDeliveryServiceTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-11-14T04:00:00Z");

    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private UserApi userApi;
    @Mock private SystemSettingsService settingsService;
    @InjectMocks private FailedDeliveryService service;

    // ---- Helpers -----------------------------------------------------------------------------

    private static ParcelDetails parcel(UUID id, ParcelStatusCode status) {
        AddressDetails address = new AddressDetails(UUID.randomUUID(), "Kasun Perera", "+94771234567", "12 Galle Road",
                null, "Galle", "Southern", null, null);
        return new ParcelDetails(id, "SD-" + id.toString().substring(0, 8), UUID.randomUUID(), null, null, address,
                address, ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, status, null, BigDecimal.TEN,
                "LKR", T0, T0);
    }

    private static Courier courier(String suffix) {
        Courier courier = new Courier();
        courier.setId(UUID.randomUUID());
        courier.setUserId(UUID.randomUUID());
        courier.setVehicleRegistration("WP " + suffix);
        return courier;
    }

    private static DeliveryAssignment assignment(UUID parcelId, Courier courier, AssignmentStatus status,
                                                 Instant assignedAt, Instant endedAt) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setParcelId(parcelId);
        assignment.setCourier(courier);
        assignment.setAssignmentStatus(status);
        assignment.setAssignedAt(assignedAt);
        assignment.setEndedAt(endedAt);
        return assignment;
    }

    private static StatusHistoryEntry entry(ParcelStatusCode status, String reason, Instant at) {
        return new StatusHistoryEntry(UUID.randomUUID(), PARCEL_ID, null, status, null, null, reason, null, null, at);
    }

    private static UserSummary account(UUID id, String name) {
        return new UserSummary(id, name, "c@example.com", "+94771234567", Role.COURIER, AccountStatus.ACTIVE, true, T0);
    }

    // ---- Queue -------------------------------------------------------------------------------

    @Test
    void attentionRowsShowTheLatestReasonTheLatestCourierAndTheAttemptCount() {
        Courier first = courier("1");
        Courier second = courier("2");
        when(parcelService.listByStatus(ParcelStatusCode.FAILED_ATTEMPT, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(parcel(PARCEL_ID, ParcelStatusCode.FAILED_ATTEMPT))));
        when(assignmentRepository.findFailedByParcelIds(List.of(PARCEL_ID))).thenReturn(List.of(
                assignment(PARCEL_ID, second, AssignmentStatus.FAILED, T0.plusSeconds(7200), T0.plusSeconds(9000)),
                assignment(PARCEL_ID, first, AssignmentStatus.FAILED, T0, T0.plusSeconds(3600))));
        when(userApi.findUser(second.getUserId())).thenReturn(Optional.of(account(second.getUserId(), "Nimal Silva")));
        when(parcelStatusService.getHistory(PARCEL_ID)).thenReturn(List.of(
                entry(ParcelStatusCode.CREATED, null, T0),
                entry(ParcelStatusCode.FAILED_ATTEMPT, "Recipient not available", T0.plusSeconds(3600)),
                entry(ParcelStatusCode.CREATED, null, T0.plusSeconds(4000)),
                entry(ParcelStatusCode.FAILED_ATTEMPT, "Shop closed", T0.plusSeconds(9000))));

        FailedDeliveryResponse row = service.list(FailedDeliveryScope.ATTENTION, 0, 20).getContent().get(0);

        assertThat(row.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(row.parcelStatus()).isEqualTo(ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(row.attemptCount()).isEqualTo(2);
        assertThat(row.courierId()).isEqualTo(second.getId());
        assertThat(row.courierName()).isEqualTo("Nimal Silva");
        assertThat(row.failureReason()).isEqualTo("Shop closed");
        assertThat(row.failedAt()).isEqualTo(T0.plusSeconds(9000));
        assertThat(row.destinationCity()).isEqualTo("Galle");
        verify(userApi, never()).findUser(first.getUserId()); // only the latest attempt's courier is named
    }

    @Test
    void closedScopeListsFailedParcelsWithTheCloseReason() {
        when(parcelService.listByStatus(ParcelStatusCode.FAILED, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(parcel(PARCEL_ID, ParcelStatusCode.FAILED))));
        when(parcelStatusService.getHistory(PARCEL_ID)).thenReturn(List.of(
                entry(ParcelStatusCode.FAILED_ATTEMPT, "Recipient not available", T0),
                entry(ParcelStatusCode.FAILED, "Address does not exist", T0.plusSeconds(60))));

        FailedDeliveryResponse row = service.list(FailedDeliveryScope.CLOSED, 1, 10).getContent().get(0);

        assertThat(row.failureReason()).isEqualTo("Address does not exist");
        assertThat(row.attemptCount()).isZero(); // no failed assignment recorded (e.g. failed before dispatch)
        assertThat(row.courierId()).isNull();
        assertThat(row.courierName()).isNull();
    }

    @Test
    void aPageLoadsItsAssignmentsInOneQueryAndEachCourierNameOnce() {
        UUID other = UUID.randomUUID();
        Courier courier = courier("1");
        when(parcelService.listByStatus(any(), any())).thenReturn(new PageImpl<>(List.of(
                parcel(PARCEL_ID, ParcelStatusCode.FAILED_ATTEMPT), parcel(other, ParcelStatusCode.FAILED_ATTEMPT))));
        when(assignmentRepository.findFailedByParcelIds(List.of(PARCEL_ID, other))).thenReturn(List.of(
                assignment(PARCEL_ID, courier, AssignmentStatus.FAILED, T0, T0.plusSeconds(60)),
                assignment(other, courier, AssignmentStatus.FAILED, T0, T0.plusSeconds(90))));
        when(userApi.findUser(courier.getUserId())).thenReturn(Optional.of(account(courier.getUserId(), "Ramesh")));
        when(parcelStatusService.getHistory(any())).thenReturn(List.of());

        List<FailedDeliveryResponse> rows = service.list(FailedDeliveryScope.ATTENTION, 0, 20).getContent();

        assertThat(rows).extracting(FailedDeliveryResponse::courierName).containsExactly("Ramesh", "Ramesh");
        verify(assignmentRepository, times(1)).findFailedByParcelIds(any());
        verify(userApi, times(1)).findUser(courier.getUserId());
    }

    @Test
    void anEmptyPageNeedsNoOtherQueries() {
        when(parcelService.listByStatus(any(), any())).thenReturn(Page.empty());

        assertThat(service.list(FailedDeliveryScope.ATTENTION, 0, 20)).isEmpty();
        verifyNoInteractions(assignmentRepository, userApi, parcelStatusService);
    }

    @Test
    void theDefaultActionComesFromSystemSettings() {
        SystemSettingsView settings = mock(SystemSettingsView.class);
        when(settings.failedDeliveryAction()).thenReturn(FailedDeliveryAction.RETURN_TO_SENDER);
        when(settingsService.current()).thenReturn(settings);

        assertThat(service.defaultAction().action()).isEqualTo(FailedDeliveryAction.RETURN_TO_SENDER);
    }

    // ---- Retry and close ---------------------------------------------------------------------

    @Test
    void retryEndsAStillOpenAssignmentThenMovesTheParcelBackToCreated() {
        when(parcelService.getParcel(PARCEL_ID)).thenReturn(parcel(PARCEL_ID, ParcelStatusCode.FAILED_ATTEMPT));
        DeliveryAssignment open = assignment(PARCEL_ID, courier("1"), AssignmentStatus.IN_PROGRESS,
                Instant.now().minusSeconds(600), null);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(open));

        FailedDecisionResponse result = service.retry(PARCEL_ID, "  Second attempt tomorrow ", ADMIN_ID);

        assertThat(result).isEqualTo(new FailedDecisionResponse(PARCEL_ID, ParcelStatusCode.CREATED));
        assertThat(open.getAssignmentStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(open.getEndedAt()).isAfterOrEqualTo(open.getAssignedAt());
        InOrder order = inOrder(assignmentRepository, parcelStatusService);
        order.verify(assignmentRepository).findOpenByParcelIdForUpdate(PARCEL_ID);
        order.verify(parcelStatusService).changeStatus(PARCEL_ID,
                StatusChange.to(ParcelStatusCode.CREATED, ADMIN_ID, "Second attempt tomorrow"));
    }

    @Test
    void retryWithoutANoteRecordsNone() {
        when(parcelService.getParcel(PARCEL_ID)).thenReturn(parcel(PARCEL_ID, ParcelStatusCode.FAILED_ATTEMPT));
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        service.retry(PARCEL_ID, "  ", ADMIN_ID);

        verify(parcelStatusService).changeStatus(PARCEL_ID, StatusChange.to(ParcelStatusCode.CREATED, ADMIN_ID, null));
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = "FAILED_ATTEMPT", mode = EnumSource.Mode.EXCLUDE)
    void onlyAFailedAttemptCanBeRetriedOrClosed(ParcelStatusCode status) {
        when(parcelService.getParcel(PARCEL_ID)).thenReturn(parcel(PARCEL_ID, status));

        assertThatThrownBy(() -> service.retry(PARCEL_ID, null, ADMIN_ID)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.close(PARCEL_ID, "Gone", ADMIN_ID)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(parcelStatusService, assignmentRepository);
    }

    @Test
    void anUnknownParcelIsNotFound() {
        when(parcelService.getParcel(PARCEL_ID)).thenThrow(new NotFoundException("Parcel not found"));

        assertThatThrownBy(() -> service.retry(PARCEL_ID, null, ADMIN_ID)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(parcelStatusService);
    }

    @Test
    void closeEndsAStillOpenAssignmentAndFailsTheParcelWithTheReason() {
        when(parcelService.getParcel(PARCEL_ID)).thenReturn(parcel(PARCEL_ID, ParcelStatusCode.FAILED_ATTEMPT));
        DeliveryAssignment open = assignment(PARCEL_ID, courier("1"), AssignmentStatus.ASSIGNED,
                Instant.now().minusSeconds(60), null);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(open));

        FailedDecisionResponse result = service.close(PARCEL_ID, " Address does not exist ", ADMIN_ID);

        assertThat(result.status()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(open.getAssignmentStatus()).isEqualTo(AssignmentStatus.FAILED);
        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(any(), change.capture());
        assertThat(change.getValue()).isEqualTo(StatusChange.failed(ADMIN_ID, "Address does not exist", null));
    }

    @Test
    void theLatestFailureIsTheLastAttemptOrClose() {
        assertThat(FailedDeliveryService.latestFailure(List.of(entry(ParcelStatusCode.CREATED, null, T0)))).isEmpty();
        assertThat(FailedDeliveryService.latestFailure(List.of(
                entry(ParcelStatusCode.FAILED_ATTEMPT, "a", T0),
                entry(ParcelStatusCode.FAILED, "b", T0.plusSeconds(1)),
                entry(ParcelStatusCode.DELIVERED, null, T0.plusSeconds(2)))).map(StatusHistoryEntry::failureReason))
                .contains("b");
    }
}

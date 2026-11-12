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
import com.swiftdrop.dispatch.dto.response.AssignmentResponse;
import com.swiftdrop.dispatch.dto.response.BulkAssignResponse;
import com.swiftdrop.dispatch.dto.response.CourierAssignmentResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_COURIER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private CourierRepository courierRepository;
    @Mock private ParcelService parcelService;
    @Mock private UserApi userApi;
    @Mock private PlatformTransactionManager transactionManager;
    private AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(assignmentRepository, courierRepository, parcelService, userApi,
                transactionManager);
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private static ParcelDetails parcel(UUID id, ParcelStatusCode status) {
        AddressDetails dropoff = new AddressDetails(UUID.randomUUID(), "Kasun Perera", "+94771234567", "12 Galle Road",
                null, "Kandy", "Central", null, null);
        return new ParcelDetails(id, "SD-" + id.toString().substring(0, 8), UUID.randomUUID(), null, null, dropoff,
                dropoff, ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, status, null, BigDecimal.TEN,
                "LKR", Instant.now(), Instant.now());
    }

    private static Courier courier(UUID id, CourierAvailability availability) {
        Courier courier = new Courier();
        courier.setId(id);
        courier.setUserId(id.equals(COURIER_ID) ? USER_ID : UUID.randomUUID());
        courier.setVehicleType("Motorbike");
        courier.setVehicleRegistration("ABC 1234");
        courier.setAvailabilityStatus(availability);
        return courier;
    }

    private static UserSummary account(AccountStatus status) {
        return new UserSummary(USER_ID, "Ramesh Perera", "ramesh@example.com", "+94771234567", Role.COURIER, status,
                true, Instant.now());
    }

    private static DeliveryAssignment openAssignment(UUID courierId, AssignmentStatus status) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setParcelId(PARCEL_ID);
        assignment.setCourier(courier(courierId, CourierAvailability.ONLINE));
        assignment.setAssignedByUserId(ADMIN_ID);
        assignment.setAssignmentStatus(status);
        assignment.setAssignedAt(Instant.now().minusSeconds(3600));
        return assignment;
    }

    private void parcelIs(UUID id, ParcelStatusCode status) {
        when(parcelService.getParcel(id)).thenReturn(parcel(id, status));
    }

    /** The courier exists, is active and has an active account. */
    private void courierIs(CourierAvailability availability) {
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(courier(COURIER_ID, availability)));
        if (availability == CourierAvailability.ONLINE) {
            when(userApi.findUser(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        }
    }

    private void savesReturnTheRow() {
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            DeliveryAssignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) {
                assignment.setId(UUID.randomUUID());
                assignment.setAssignedAt(Instant.now());
            }
            return assignment;
        });
    }

    private void transactionsWork() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    // ---- Assign ------------------------------------------------------------------------------

    @Test
    void assignsAWaitingParcelToAnOnlineCourierWithoutChangingTheParcel() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        courierIs(CourierAvailability.ONLINE);
        savesReturnTheRow();

        AssignmentResponse response = service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID);

        ArgumentCaptor<DeliveryAssignment> saved = ArgumentCaptor.forClass(DeliveryAssignment.class);
        verify(assignmentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getParcelId()).isEqualTo(PARCEL_ID);
        assertThat(saved.getValue().getCourier().getId()).isEqualTo(COURIER_ID);
        assertThat(saved.getValue().getAssignedByUserId()).isEqualTo(ADMIN_ID);
        assertThat(saved.getValue().getAssignmentStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(saved.getValue().getEndedAt()).isNull();
        assertThat(response.courierUserId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void refusesAParcelThatDoesNotExist() {
        when(parcelService.getParcel(PARCEL_ID)).thenThrow(new NotFoundException("Parcel not found"));

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID)).isInstanceOf(NotFoundException.class);
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    void refusesAParcelThatIsNoLongerWaiting(ParcelStatusCode status) {
        parcelIs(PARCEL_ID, status);

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not been picked up");
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesAParcelThatAlreadyHasACourier() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(assignmentRepository.existsByParcelIdAndEndedAtIsNull(PARCEL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessage(AssignmentService.ALREADY_ASSIGNED);
        verifyNoInteractions(courierRepository);
    }

    @Test
    void refusesACourierThatDoesNotExist() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(NotFoundException.class).hasMessage("Courier not found");
    }

    @Test
    void refusesACourierWhoIsOnABreakOrOffline() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        courierIs(CourierAvailability.ON_BREAK);
        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("on a break");

        courierIs(CourierAvailability.OFFLINE);
        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("offline");
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesADeactivatedCourier() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        Courier deactivated = courier(COURIER_ID, CourierAvailability.ONLINE);
        deactivated.setActive(false);
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("deactivated");
    }

    @Test
    void refusesACourierWhoseAccountIsNotActive() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(courierRepository.findById(COURIER_ID))
                .thenReturn(Optional.of(courier(COURIER_ID, CourierAvailability.ONLINE)));
        when(userApi.findUser(USER_ID)).thenReturn(Optional.of(account(AccountStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("account is not active");
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void losingTheRaceForTheParcelIsTheSameConflict() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        courierIs(CourierAvailability.ONLINE);
        when(assignmentRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("ux_delivery_assignments_open_per_parcel"));

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessage(AssignmentService.ALREADY_ASSIGNED);
    }

    // ---- Bulk --------------------------------------------------------------------------------

    @Test
    void bulkAssignsWhatItCanAndReportsTheRestEachInItsOwnTransaction() {
        UUID taken = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        transactionsWork();
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        parcelIs(taken, ParcelStatusCode.CREATED);
        when(parcelService.getParcel(missing)).thenThrow(new NotFoundException("Parcel not found"));
        when(assignmentRepository.existsByParcelIdAndEndedAtIsNull(PARCEL_ID)).thenReturn(false);
        when(assignmentRepository.existsByParcelIdAndEndedAtIsNull(taken)).thenReturn(true);
        courierIs(CourierAvailability.ONLINE);
        savesReturnTheRow();

        BulkAssignResponse result = service.assignAll(List.of(PARCEL_ID, taken, missing, PARCEL_ID), COURIER_ID,
                ADMIN_ID);

        assertThat(result.assigned()).extracting(AssignmentResponse::parcelId).containsExactly(PARCEL_ID);
        assertThat(result.failed()).containsExactly(
                new BulkAssignResponse.Failure(taken, AssignmentService.ALREADY_ASSIGNED),
                new BulkAssignResponse.Failure(missing, "Parcel not found"));
        verify(transactionManager, times(3)).getTransaction(any()); // the duplicate id is handled once
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, times(2)).rollback(any());
    }

    @Test
    void bulkReportsEveryParcelWhenTheCourierCannotTakeAny() {
        UUID second = UUID.randomUUID();
        transactionsWork();
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        parcelIs(second, ParcelStatusCode.CREATED);
        courierIs(CourierAvailability.OFFLINE);

        BulkAssignResponse result = service.assignAll(List.of(PARCEL_ID, second), COURIER_ID, ADMIN_ID);

        assertThat(result.assigned()).isEmpty();
        assertThat(result.failed()).extracting(BulkAssignResponse.Failure::parcelId).containsExactly(PARCEL_ID, second);
        assertThat(result.failed()).allSatisfy(failure -> assertThat(failure.reason()).contains("offline"));
    }

    @Test
    void bulkCarriesOnAfterAnUnexpectedError() {
        UUID second = UUID.randomUUID();
        transactionsWork();
        when(parcelService.getParcel(PARCEL_ID)).thenThrow(new DataAccessResourceFailureException("database down"));
        parcelIs(second, ParcelStatusCode.CREATED);
        courierIs(CourierAvailability.ONLINE);
        savesReturnTheRow();

        BulkAssignResponse result = service.assignAll(List.of(PARCEL_ID, second), COURIER_ID, ADMIN_ID);

        assertThat(result.assigned()).extracting(AssignmentResponse::parcelId).containsExactly(second);
        assertThat(result.failed()).containsExactly(
                new BulkAssignResponse.Failure(PARCEL_ID, "The parcel could not be assigned"));
    }

    // ---- Reassign ----------------------------------------------------------------------------

    @Test
    void reassignEndsTheCurrentAssignmentBeforeCreatingTheNewOne() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        DeliveryAssignment current = openAssignment(OTHER_COURIER_ID, AssignmentStatus.ASSIGNED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(current));
        courierIs(CourierAvailability.ONLINE);
        savesReturnTheRow();

        AssignmentResponse response = service.reassign(PARCEL_ID, COURIER_ID, "  Vehicle broke down ", ADMIN_ID);

        assertThat(current.getAssignmentStatus()).isEqualTo(AssignmentStatus.REASSIGNED);
        assertThat(current.getEndedAt()).isNotNull().isAfterOrEqualTo(current.getAssignedAt());
        assertThat(current.getReassignmentReason()).isEqualTo("Vehicle broke down");
        assertThat(response.courierId()).isEqualTo(COURIER_ID);
        assertThat(response.status()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(response.assignedByUserId()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<DeliveryAssignment> saved = ArgumentCaptor.forClass(DeliveryAssignment.class);
        InOrder order = inOrder(assignmentRepository);
        order.verify(assignmentRepository, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().get(0)).isSameAs(current);
        assertThat(saved.getAllValues().get(1).getCourier().getId()).isEqualTo(COURIER_ID);
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"})
    void aParcelTheOldCourierAlreadyHasStartsInProgressWithTheNewOne(ParcelStatusCode status) {
        parcelIs(PARCEL_ID, status);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID))
                .thenReturn(Optional.of(openAssignment(OTHER_COURIER_ID, AssignmentStatus.IN_PROGRESS)));
        courierIs(CourierAvailability.ONLINE);
        savesReturnTheRow();

        AssignmentResponse response = service.reassign(PARCEL_ID, COURIER_ID, "Hand over", ADMIN_ID);

        assertThat(response.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = {"DELIVERED", "FAILED"})
    void aDeliveredOrFailedParcelCannotBeReassigned(ParcelStatusCode status) {
        parcelIs(PARCEL_ID, status);

        assertThatThrownBy(() -> service.reassign(PARCEL_ID, COURIER_ID, "Late", ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("delivered or failed");
        verify(assignmentRepository, never()).findOpenByParcelIdForUpdate(any());
    }

    @Test
    void aParcelWithoutACourierCannotBeReassigned() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(PARCEL_ID, COURIER_ID, "Late", ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("assign it instead");
    }

    @Test
    void reassigningToTheSameCourierIsRefused() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID))
                .thenReturn(Optional.of(openAssignment(COURIER_ID, AssignmentStatus.ASSIGNED)));

        assertThatThrownBy(() -> service.reassign(PARCEL_ID, COURIER_ID, "Late", ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already assigned to this courier");
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void reassignAppliesTheCourierRulesAndKeepsTheCurrentAssignment() {
        parcelIs(PARCEL_ID, ParcelStatusCode.IN_TRANSIT);
        DeliveryAssignment current = openAssignment(OTHER_COURIER_ID, AssignmentStatus.IN_PROGRESS);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(current));
        courierIs(CourierAvailability.OFFLINE);

        assertThatThrownBy(() -> service.reassign(PARCEL_ID, COURIER_ID, "Late", ADMIN_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("offline");
        assertThat(current.getAssignmentStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(current.getEndedAt()).isNull();
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    // ---- Unassign ----------------------------------------------------------------------------

    @Test
    void unassignCancelsTheOpenAssignmentOfAWaitingParcel() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        DeliveryAssignment current = openAssignment(COURIER_ID, AssignmentStatus.ASSIGNED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(current));
        savesReturnTheRow();

        AssignmentResponse response = service.unassign(PARCEL_ID, " Customer asked to wait ");

        assertThat(response.status()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(response.endedAt()).isNotNull();
        assertThat(response.reason()).isEqualTo("Customer asked to wait");
    }

    @Test
    void unassignWithoutAReasonStoresNone() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID))
                .thenReturn(Optional.of(openAssignment(COURIER_ID, AssignmentStatus.ASSIGNED)));
        savesReturnTheRow();

        assertThat(service.unassign(PARCEL_ID, "   ").reason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    void onlyAParcelThatHasNotBeenPickedUpCanBeUnassigned(ParcelStatusCode status) {
        parcelIs(PARCEL_ID, status);

        assertThatThrownBy(() -> service.unassign(PARCEL_ID, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not been picked up");
        verify(assignmentRepository, never()).findOpenByParcelIdForUpdate(any());
    }

    @Test
    void aParcelWithoutACourierCannotBeUnassigned() {
        parcelIs(PARCEL_ID, ParcelStatusCode.CREATED);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(PARCEL_ID, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not assigned");
    }

    @Test
    void anAssignmentNeverEndsBeforeItStarted() {
        DeliveryAssignment assignment = openAssignment(COURIER_ID, AssignmentStatus.ASSIGNED);
        Instant started = assignment.getAssignedAt();

        assertThat(AssignmentService.endTime(assignment, started.minusSeconds(5))).isEqualTo(started);
        assertThat(AssignmentService.endTime(assignment, started.plusSeconds(5))).isEqualTo(started.plusSeconds(5));
    }

    // ---- Courier's assignments ---------------------------------------------------------------

    @Test
    void listsACouriersOpenAssignmentsWithTheirParcelsLoadedInOneCall() {
        UUID gone = UUID.randomUUID();
        DeliveryAssignment first = openAssignment(COURIER_ID, AssignmentStatus.IN_PROGRESS);
        DeliveryAssignment second = openAssignment(COURIER_ID, AssignmentStatus.ASSIGNED);
        second.setParcelId(gone);
        when(courierRepository.existsById(COURIER_ID)).thenReturn(true);
        when(assignmentRepository.findByCourier_IdAndEndedAtIsNull(any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(parcelService.findAllByIds(Set.of(PARCEL_ID, gone)))
                .thenReturn(List.of(parcel(PARCEL_ID, ParcelStatusCode.IN_TRANSIT)));

        Page<CourierAssignmentResponse> rows = service.listForCourier(COURIER_ID, true, 0, 20);

        CourierAssignmentResponse row = rows.getContent().get(0);
        assertThat(row.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(row.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(row.trackingNumber()).startsWith("SD-");
        assertThat(row.parcelStatus()).isEqualTo(ParcelStatusCode.IN_TRANSIT);
        assertThat(row.destinationCity()).isEqualTo("Kandy");
        assertThat(rows.getContent().get(1).trackingNumber()).isNull(); // parcel not readable
        verify(parcelService, times(1)).findAllByIds(any());
    }

    @Test
    void historyAndAllUseTheirOwnQueriesNewestFirst() {
        when(courierRepository.existsById(COURIER_ID)).thenReturn(true);
        when(assignmentRepository.findByCourier_IdAndEndedAtIsNotNull(any(), any())).thenReturn(Page.empty());
        when(assignmentRepository.findByCourier_Id(any(), any())).thenReturn(Page.empty());

        service.listForCourier(COURIER_ID, false, 1, 10);
        service.listForCourier(COURIER_ID, null, 0, 5);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findByCourier_IdAndEndedAtIsNotNull(any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getSort()).isEqualTo(AssignmentService.NEWEST_FIRST);
        verify(assignmentRepository).findByCourier_Id(any(), any());
        verifyNoInteractions(parcelService); // empty pages need no parcels
    }

    @Test
    void listingAnUnknownCourierIsNotFound() {
        when(courierRepository.existsById(COURIER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listForCourier(COURIER_ID, null, 0, 20))
                .isInstanceOf(NotFoundException.class);
    }
}

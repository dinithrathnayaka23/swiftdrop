package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.dto.request.CourierFilter;
import com.swiftdrop.dispatch.dto.request.CreateCourierRequest;
import com.swiftdrop.dispatch.dto.request.UpdateCourierRequest;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.repository.AvailabilityCount;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.dispatch.repository.WorkloadCount;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ZONE_A = UUID.randomUUID();
    private static final UUID ZONE_B = UUID.randomUUID();

    @Mock private CourierRepository courierRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private UserApi userApi;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private CourierService service;

    // ---- Helpers -----------------------------------------------------------------------------

    private static UserSummary user(AccountStatus status) {
        return new UserSummary(USER_ID, "Ramesh Perera", "ramesh@example.com", "+94771234567", Role.COURIER, status,
                true, Instant.now());
    }

    private static Courier courier() {
        Courier courier = new Courier();
        courier.setId(COURIER_ID);
        courier.setUserId(USER_ID);
        courier.setVehicleType("Motorbike");
        courier.setVehicleRegistration("ABC-1234");
        courier.setAvailabilityStatus(CourierAvailability.OFFLINE);
        courier.getServiceAreaIds().add(ZONE_A);
        return courier;
    }

    private static CreateCourierRequest createRequest(Set<UUID> areas) {
        return new CreateCourierRequest("Ramesh Perera", " ramesh@example.com ", "+94771234567", "courier2026",
                "Motorbike", "abc  1234", areas);
    }

    private void courierExists() {
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(courier()));
    }

    private void userExists(AccountStatus status) {
        when(userApi.findUser(USER_ID)).thenReturn(Optional.of(user(status)));
    }

    private void createPassesTheChecks() {
        when(userApi.emailExists("ramesh@example.com")).thenReturn(false);
        when(courierRepository.existsByVehicleRegistration("ABC 1234")).thenReturn(false);
        when(passwordEncoder.encode("courier2026")).thenReturn("hashed");
        when(userApi.createUser(any(NewUser.class))).thenReturn(
                new UserAccount(USER_ID, "Ramesh Perera", "ramesh@example.com", "hashed", null, Role.COURIER,
                        AccountStatus.ACTIVE, true));
    }

    // ---- Create ------------------------------------------------------------------------------

    @Test
    void createsAVerifiedCourierAccountAndAnOfflineProfile() {
        createPassesTheChecks();
        when(courierRepository.saveAndFlush(any(Courier.class))).thenAnswer(call -> {
            Courier saved = call.getArgument(0);
            saved.setId(COURIER_ID);
            return saved;
        });
        userExists(AccountStatus.ACTIVE);

        CourierResponse response = service.create(createRequest(Set.of(ZONE_A, ZONE_B)));

        ArgumentCaptor<NewUser> account = ArgumentCaptor.forClass(NewUser.class);
        verify(userApi).createUser(account.capture());
        assertThat(account.getValue().role()).isEqualTo(Role.COURIER);
        assertThat(account.getValue().accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getValue().emailVerified()).isTrue();
        assertThat(account.getValue().email()).isEqualTo("ramesh@example.com");
        assertThat(account.getValue().passwordHash()).isEqualTo("hashed");

        assertThat(response.id()).isEqualTo(COURIER_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.availability()).isEqualTo(CourierAvailability.OFFLINE);
        assertThat(response.vehicleRegistration()).isEqualTo("ABC 1234");
        assertThat(response.serviceAreaIds()).containsExactlyInAnyOrder(ZONE_A, ZONE_B);
        assertThat(response.workload()).isEqualTo(CourierWorkload.EMPTY);
    }

    @Test
    void createWithoutServiceAreasIsFine() {
        createPassesTheChecks();
        when(courierRepository.saveAndFlush(any(Courier.class))).thenAnswer(call -> call.getArgument(0));
        when(userApi.findUser(USER_ID)).thenReturn(Optional.of(user(AccountStatus.ACTIVE)));

        assertThat(service.create(createRequest(null)).serviceAreaIds()).isEmpty();
    }

    @Test
    void createRefusesAnEmailThatIsAlreadyUsed() {
        when(userApi.emailExists("ramesh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null))).isInstanceOf(ConflictException.class);

        verify(userApi, never()).createUser(any());
        verifyNoInteractions(courierRepository);
    }

    @Test
    void createRefusesAVehicleThatAnotherCourierHas() {
        when(userApi.emailExists("ramesh@example.com")).thenReturn(false);
        when(courierRepository.existsByVehicleRegistration("ABC 1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null))).isInstanceOf(ConflictException.class);

        verify(userApi, never()).createUser(any());
    }

    @Test
    void createReportsAConflictWhenTheDatabaseRefusesTheCourier() {
        createPassesTheChecks();
        when(courierRepository.saveAndFlush(any(Courier.class))).thenThrow(new DataIntegrityViolationException("x"));

        assertThatThrownBy(() -> service.create(createRequest(Set.of(ZONE_A)))).isInstanceOf(ConflictException.class);
    }

    @Test
    void registrationsAreStoredUpperCaseWithSingleSpaces() {
        assertThat(CourierService.normalizeRegistration("  wp  cab 1234 ")).isEqualTo("WP CAB 1234");
        assertThatThrownBy(() -> CourierService.normalizeRegistration("   ")).isInstanceOf(BadRequestException.class);
    }

    // ---- Update ------------------------------------------------------------------------------

    @Test
    void updateNeedsAtLeastOneField() {
        assertThatThrownBy(() -> service.update(COURIER_ID, new UpdateCourierRequest(null, null, null)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(courierRepository);
    }

    @Test
    void updateChangesOnlyTheFieldsGiven() {
        courierExists();
        userExists(AccountStatus.ACTIVE);
        when(courierRepository.existsByVehicleRegistrationAndIdNot("XYZ 99", COURIER_ID)).thenReturn(false);
        when(courierRepository.saveAndFlush(any(Courier.class))).thenAnswer(call -> call.getArgument(0));

        CourierResponse response = service.update(COURIER_ID, new UpdateCourierRequest(" Van ", "xyz 99", Set.of(ZONE_B)));

        assertThat(response.vehicleType()).isEqualTo("Van");
        assertThat(response.vehicleRegistration()).isEqualTo("XYZ 99");
        assertThat(response.serviceAreaIds()).containsExactly(ZONE_B);
    }

    @Test
    void updateKeepsTheAreasWhenNoneAreGivenAndClearsThemWhenEmpty() {
        courierExists();
        userExists(AccountStatus.ACTIVE);
        when(courierRepository.saveAndFlush(any(Courier.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.update(COURIER_ID, new UpdateCourierRequest("Van", null, null)).serviceAreaIds())
                .containsExactly(ZONE_A);

        courierExists();
        assertThat(service.update(COURIER_ID, new UpdateCourierRequest(null, null, Set.of())).serviceAreaIds())
                .isEmpty();
    }

    @Test
    void updateDoesNotCheckTheRegistrationWhenItIsUnchanged() {
        courierExists();
        userExists(AccountStatus.ACTIVE);
        when(courierRepository.saveAndFlush(any(Courier.class))).thenAnswer(call -> call.getArgument(0));

        service.update(COURIER_ID, new UpdateCourierRequest(null, "abc-1234", null));

        verify(courierRepository, never()).existsByVehicleRegistrationAndIdNot(any(), any());
    }

    @Test
    void updateRefusesARegistrationAnotherCourierHas() {
        courierExists();
        when(courierRepository.existsByVehicleRegistrationAndIdNot("XYZ 99", COURIER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(COURIER_ID, new UpdateCourierRequest(null, "XYZ 99", null)))
                .isInstanceOf(ConflictException.class);

        verify(courierRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateReportsAConflictForAnUnknownServiceArea() {
        courierExists();
        when(courierRepository.saveAndFlush(any(Courier.class))).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.update(COURIER_ID, new UpdateCourierRequest(null, null, Set.of(ZONE_B))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateOfAnUnknownCourierIsNotFound() {
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(COURIER_ID, new UpdateCourierRequest("Van", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- Availability ------------------------------------------------------------------------

    @Test
    void anActiveCourierCanGoOnlineOnBreakAndOffline() {
        courierExists();
        userExists(AccountStatus.ACTIVE);
        assertThat(service.changeAvailability(COURIER_ID, CourierAvailability.ONLINE).availability())
                .isEqualTo(CourierAvailability.ONLINE);

        courierExists();
        assertThat(service.changeAvailability(COURIER_ID, CourierAvailability.ON_BREAK).availability())
                .isEqualTo(CourierAvailability.ON_BREAK);
    }

    @Test
    void aDeactivatedCourierCannotGoOnline() {
        Courier inactive = courier();
        inactive.setActive(false);
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(inactive));
        userExists(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeAvailability(COURIER_ID, CourierAvailability.ONLINE))
                .isInstanceOf(ConflictException.class);
        assertThat(inactive.getAvailabilityStatus()).isEqualTo(CourierAvailability.OFFLINE);
    }

    @Test
    void aCourierWhoseAccountCannotSignInCannotGoOnlineButCanGoOffline() {
        Courier suspended = courier();
        suspended.setAvailabilityStatus(CourierAvailability.ON_BREAK);
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.of(suspended));
        userExists(AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.changeAvailability(COURIER_ID, CourierAvailability.ONLINE))
                .isInstanceOf(ConflictException.class);

        assertThat(service.changeAvailability(COURIER_ID, CourierAvailability.OFFLINE).availability())
                .isEqualTo(CourierAvailability.OFFLINE);
    }

    @Test
    void availabilityOfAnUnknownCourierIsNotFound() {
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeAvailability(COURIER_ID, CourierAvailability.ONLINE))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- Read and workload -------------------------------------------------------------------

    @Test
    void getShowsTodaysWorkloadFromTheAssignments() {
        courierExists();
        userExists(AccountStatus.ACTIVE);
        when(assignmentRepository.countWorkload(any(), any())).thenReturn(List.of(
                new WorkloadCount(COURIER_ID, AssignmentStatus.ASSIGNED, 2),
                new WorkloadCount(COURIER_ID, AssignmentStatus.IN_PROGRESS, 1),
                new WorkloadCount(COURIER_ID, AssignmentStatus.COMPLETED, 5)));

        Instant before = startOfTodayInSriLanka();
        CourierResponse response = service.get(COURIER_ID);
        Instant after = startOfTodayInSriLanka();

        assertThat(response.workload()).isEqualTo(new CourierWorkload(2, 1, 5, 3, 8));
        assertThat(response.fullName()).isEqualTo("Ramesh Perera");
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);

        ArgumentCaptor<Instant> startOfDay = ArgumentCaptor.forClass(Instant.class);
        verify(assignmentRepository).countWorkload(eq(Set.of(COURIER_ID)), startOfDay.capture());
        assertThat(startOfDay.getValue()).isIn(before, after);
    }

    @Test
    void aCourierWithNoAssignmentsHasAnEmptyWorkload() {
        courierExists();
        userExists(AccountStatus.ACTIVE);

        assertThat(service.get(COURIER_ID).workload()).isEqualTo(CourierWorkload.EMPTY);
    }

    @Test
    void getOfAnUnknownCourierIsNotFound() {
        when(courierRepository.findById(COURIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(COURIER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchAsksTheUserModuleWhichCourierAccountsMatch() {
        when(userApi.listUsers(new UserSearch("ramesh", Role.COURIER, null),
                PageRequest.of(0, CourierService.MAX_SEARCH_MATCHES)))
                .thenReturn(new PageImpl<>(List.of(user(AccountStatus.ACTIVE))));
        when(courierRepository.findAll(ArgumentMatchers.<Specification<Courier>>any(), eq(PageRequest.of(1, 5,
                CourierService.NEWEST_FIRST)))).thenReturn(new PageImpl<>(List.of(courier())));
        userExists(AccountStatus.ACTIVE);

        Page<CourierResponse> page = service.list(
                new CourierFilter(" ramesh ", CourierAvailability.ONLINE, ZONE_A, "motorbike", true), 1, 5);

        assertThat(page.getContent()).extracting(CourierResponse::fullName).containsExactly("Ramesh Perera");
    }

    @Test
    void listingWithoutASearchDoesNotAskTheUserModule() {
        when(courierRepository.findAll(ArgumentMatchers.<Specification<Courier>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(courier())));
        userExists(AccountStatus.ACTIVE);

        assertThat(service.list(CourierFilter.none(), 0, 20).getContent()).hasSize(1);

        verify(userApi, never()).listUsers(any(), any());
    }

    @Test
    void anEmptyPageDoesNotQueryTheWorkloads() {
        when(courierRepository.findAll(ArgumentMatchers.<Specification<Courier>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(service.list(CourierFilter.none(), 0, 20).getContent()).isEmpty();

        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void summaryCountsActiveCouriersAndFillsInZeros() {
        when(courierRepository.countActiveByAvailability()).thenReturn(List.of(
                new AvailabilityCount(CourierAvailability.ONLINE, 18), new AvailabilityCount(CourierAvailability.OFFLINE, 7)));

        assertThat(service.summary()).isEqualTo(new CourierSummaryResponse(25, 18, 0, 7));
    }

    private static Instant startOfTodayInSriLanka() {
        return LocalDate.now(CourierService.SRI_LANKA).atStartOfDay(CourierService.SRI_LANKA).toInstant();
    }
}

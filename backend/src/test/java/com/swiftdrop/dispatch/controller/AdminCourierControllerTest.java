package com.swiftdrop.dispatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.dispatch.dto.request.ChangeAvailabilityRequest;
import com.swiftdrop.dispatch.dto.request.CourierFilter;
import com.swiftdrop.dispatch.dto.request.CreateCourierRequest;
import com.swiftdrop.dispatch.dto.request.UpdateCourierRequest;
import com.swiftdrop.dispatch.dto.response.CourierAssignmentResponse;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.service.AssignmentService;
import com.swiftdrop.dispatch.service.CourierService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminCourierControllerTest {

    private static final UUID COURIER_ID = UUID.randomUUID();

    @Mock private CourierService courierService;
    @Mock private AssignmentService assignmentService;
    @InjectMocks private AdminCourierController controller;

    private static CourierResponse response() {
        return new CourierResponse(COURIER_ID, UUID.randomUUID(), "Ramesh Perera", "ramesh@example.com",
                "+94771234567", "Motorbike", "ABC 1234", CourierAvailability.OFFLINE, true, AccountStatus.ACTIVE,
                Set.of(), CourierWorkload.EMPTY, Instant.now());
    }

    @Test
    void onlyTheDispatcherAdminMayUseIt() {
        assertThat(AdminCourierController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void listsWithTheFiltersAndPageAsked() {
        UUID zone = UUID.randomUUID();
        CourierFilter filter = new CourierFilter("ram", CourierAvailability.ONLINE, zone, "van", true);
        when(courierService.list(filter, 1, 50)).thenReturn(new PageImpl<>(List.of(response())));

        var page = controller.list("ram", CourierAvailability.ONLINE, zone, "van", true, 1, 50);

        assertThat(page.getContent()).extracting(CourierResponse::id).containsExactly(COURIER_ID);
    }

    @Test
    void refusesPagesThatDoNotExist() {
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(courierService);
    }

    @Test
    void showsTheSummaryAndOneCourier() {
        CourierSummaryResponse summary = new CourierSummaryResponse(28, 18, 3, 7);
        when(courierService.summary()).thenReturn(summary);
        when(courierService.get(COURIER_ID)).thenReturn(response());

        assertThat(controller.summary()).isEqualTo(summary);
        assertThat(controller.get(COURIER_ID).id()).isEqualTo(COURIER_ID);
    }

    @Test
    void createAnswers201WithTheLocationOfTheNewCourier() {
        CreateCourierRequest request = new CreateCourierRequest("Ramesh Perera", "ramesh@example.com", "+94771234567",
                "courier2026", "Motorbike", "ABC 1234", null);
        when(courierService.create(request)).thenReturn(response());

        var created = controller.create(request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/v1/admin/couriers/" + COURIER_ID);
        assertThat(created.getBody().id()).isEqualTo(COURIER_ID);
    }

    @Test
    void updatesTheProfileAndTheAvailability() {
        UpdateCourierRequest update = new UpdateCourierRequest("Van", null, null);
        when(courierService.update(COURIER_ID, update)).thenReturn(response());
        when(courierService.changeAvailability(COURIER_ID, CourierAvailability.ON_BREAK)).thenReturn(response());

        controller.update(COURIER_ID, update);
        controller.changeAvailability(COURIER_ID, new ChangeAvailabilityRequest(CourierAvailability.ON_BREAK));

        verify(courierService).update(COURIER_ID, update);
        verify(courierService).changeAvailability(COURIER_ID, CourierAvailability.ON_BREAK);
    }

    @Test
    void listsTheCouriersAssignmentsForTheDrawerTabs() {
        CourierAssignmentResponse row = new CourierAssignmentResponse(UUID.randomUUID(), UUID.randomUUID(),
                AssignmentStatus.ASSIGNED, Instant.now(), null, null, "SD-12345678", ParcelStatusCode.CREATED, "Kandy");
        when(assignmentService.listForCourier(COURIER_ID, true, 0, 20)).thenReturn(new PageImpl<>(List.of(row)));

        assertThat(controller.assignments(COURIER_ID, true, 0, 20).getContent()).containsExactly(row);
    }

    @Test
    void refusesAssignmentPagesThatDoNotExist() {
        assertThatThrownBy(() -> controller.assignments(COURIER_ID, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.assignments(COURIER_ID, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(assignmentService);
    }
}

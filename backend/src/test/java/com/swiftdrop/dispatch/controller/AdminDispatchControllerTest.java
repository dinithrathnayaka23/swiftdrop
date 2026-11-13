package com.swiftdrop.dispatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.dto.request.AssignParcelRequest;
import com.swiftdrop.dispatch.dto.request.BulkAssignRequest;
import com.swiftdrop.dispatch.dto.request.ReassignParcelRequest;
import com.swiftdrop.dispatch.dto.request.UnassignParcelRequest;
import com.swiftdrop.dispatch.dto.response.AssignmentResponse;
import com.swiftdrop.dispatch.dto.response.BulkAssignResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.service.AssignmentService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminDispatchControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(ADMIN_ID.toString(),
            Set.of(Role.DISPATCHER_ADMIN));

    @Mock private AssignmentService assignmentService;
    @InjectMocks private AdminDispatchController controller;

    private static AssignmentResponse assignment(AssignmentStatus status) {
        return new AssignmentResponse(UUID.randomUUID(), PARCEL_ID, COURIER_ID, UUID.randomUUID(), status, ADMIN_ID,
                Instant.now(), null, null);
    }

    @Test
    void onlyTheDispatcherAdminMayUseIt() {
        assertThat(AdminDispatchController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void assignCreatesTheAssignmentAsTheSignedInAdmin() {
        AssignmentResponse created = assignment(AssignmentStatus.ASSIGNED);
        when(assignmentService.assign(PARCEL_ID, COURIER_ID, ADMIN_ID)).thenReturn(created);

        ResponseEntity<AssignmentResponse> response = controller.assign(new AssignParcelRequest(PARCEL_ID, COURIER_ID),
                ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString(
                "/api/v1/admin/dispatch/assignments/" + created.id());
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void bulkReturnsWhatWasAndWasNotAssigned() {
        UUID other = UUID.randomUUID();
        BulkAssignResponse result = new BulkAssignResponse(List.of(),
                List.of(new BulkAssignResponse.Failure(other, "This courier is offline")));
        when(assignmentService.assignAll(List.of(other), COURIER_ID, ADMIN_ID)).thenReturn(result);

        assertThat(controller.assignAll(new BulkAssignRequest(COURIER_ID, List.of(other)), ADMIN)).isEqualTo(result);
    }

    @Test
    void reassignAndUnassignPassTheReason() {
        AssignmentResponse moved = assignment(AssignmentStatus.ASSIGNED);
        when(assignmentService.reassign(PARCEL_ID, COURIER_ID, "Vehicle broke down", ADMIN_ID)).thenReturn(moved);
        AssignmentResponse cancelled = assignment(AssignmentStatus.CANCELLED);
        when(assignmentService.unassign(PARCEL_ID, "Customer asked")).thenReturn(cancelled);

        assertThat(controller.reassign(PARCEL_ID, new ReassignParcelRequest(COURIER_ID, "Vehicle broke down"), ADMIN))
                .isEqualTo(moved);
        assertThat(controller.unassign(PARCEL_ID, new UnassignParcelRequest("Customer asked"))).isEqualTo(cancelled);
    }

    @Test
    void unassignNeedsNoBody() {
        controller.unassign(PARCEL_ID, null);

        verify(assignmentService).unassign(PARCEL_ID, null);
    }

    @Test
    void readsOneAssignment() {
        AssignmentResponse found = assignment(AssignmentStatus.ASSIGNED);
        when(assignmentService.get(found.id())).thenReturn(found);

        assertThat(controller.get(found.id())).isEqualTo(found);
    }
}

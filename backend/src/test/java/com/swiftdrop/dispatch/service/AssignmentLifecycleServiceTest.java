package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class AssignmentLifecycleServiceTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final Instant ASSIGNED_AT = Instant.parse("2026-11-13T08:00:00Z");

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @InjectMocks private AssignmentLifecycleService service;

    private static ParcelStatusChangedEvent event(UUID parcelId, String newStatus, Instant occurredAt) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION, parcelId, "SD-12345678", null, "CREATED", newStatus, 2,
                UUID.randomUUID(), UUID.randomUUID(), occurredAt);
    }

    private static ParcelStatusChangedEvent event(String newStatus) {
        return event(PARCEL_ID, newStatus, ASSIGNED_AT.plusSeconds(600));
    }

    private DeliveryAssignment openAssignment(AssignmentStatus status) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setParcelId(PARCEL_ID);
        assignment.setCourier(new Courier());
        assignment.setAssignmentStatus(status);
        assignment.setAssignedAt(ASSIGNED_AT);
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(assignment));
        return assignment;
    }

    @ParameterizedTest
    @ValueSource(strings = {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"})
    void pickupAndTransitStartTheAssignment(String status) {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.ASSIGNED);

        service.apply(event(status));

        assertThat(assignment.getAssignmentStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(assignment.getEndedAt()).isNull();
    }

    @Test
    void aRepeatedTransitEventLeavesAStartedAssignmentAsItIs() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.IN_PROGRESS);

        service.apply(event("IN_TRANSIT"));

        assertThat(assignment.getAssignmentStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(assignment.getEndedAt()).isNull();
    }

    @Test
    void deliveredCompletesTheAssignmentWhenTheParcelWasDelivered() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.IN_PROGRESS);
        Instant deliveredAt = ASSIGNED_AT.plusSeconds(3600);

        service.apply(event(PARCEL_ID, "DELIVERED", deliveredAt));

        assertThat(assignment.getAssignmentStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getEndedAt()).isEqualTo(deliveredAt);
    }

    @Test
    void failedEndsTheAssignmentAsFailedNotCompleted() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.IN_PROGRESS);

        service.apply(event("FAILED"));

        assertThat(assignment.getAssignmentStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(assignment.getEndedAt()).isNotNull();
    }

    @Test
    void aDeliveryWhosePickupEventWasMissedStillCompletes() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.ASSIGNED);

        service.apply(event("DELIVERED"));

        assertThat(assignment.getAssignmentStatus()).isEqualTo(AssignmentStatus.COMPLETED);
    }

    @Test
    void theEndIsNeverBeforeTheAssignmentStarted() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.IN_PROGRESS);

        service.apply(event(PARCEL_ID, "DELIVERED", ASSIGNED_AT.minusSeconds(30)));

        assertThat(assignment.getEndedAt()).isEqualTo(ASSIGNED_AT);
    }

    @Test
    void withoutATimeTheAssignmentEndsNow() {
        DeliveryAssignment assignment = openAssignment(AssignmentStatus.IN_PROGRESS);
        Instant before = Instant.now();

        service.apply(event(PARCEL_ID, "FAILED", null));

        assertThat(assignment.getEndedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void anEventForAParcelWithoutAnOpenAssignmentChangesNothing() {
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        service.apply(event("DELIVERED")); // e.g. a duplicate after the assignment was completed

        verify(assignmentRepository).findOpenByParcelIdForUpdate(PARCEL_ID);
        verifyNoMoreInteractions(assignmentRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "FAILED_ATTEMPT", "delivered", ""})
    void otherAndUnknownStatusesAreIgnored(String status) {
        service.apply(event(status));

        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void incompleteEventsAreIgnored() {
        service.apply(null);
        service.apply(event(null, "DELIVERED", Instant.now()));
        service.apply(event(PARCEL_ID, null, Instant.now()));

        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void databaseErrorsAreThrownSoTheMessageIsRetried() {
        when(assignmentRepository.findOpenByParcelIdForUpdate(PARCEL_ID))
                .thenThrow(new DataAccessResourceFailureException("database down"));

        assertThatThrownBy(() -> service.apply(event("DELIVERED")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}

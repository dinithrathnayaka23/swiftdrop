package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentLookupServiceTest {

    private static final UUID PARCEL = UUID.randomUUID();
    private static final UUID OTHER_PARCEL = UUID.randomUUID();
    private static final UUID COURIER_USER = UUID.randomUUID();

    @Mock private DeliveryAssignmentRepository assignments;
    @Mock private CourierRepository couriers;
    @InjectMocks private AssignmentLookupService lookup;

    @Test
    void reportsTheParcelsThatHaveAnOpenAssignment() {
        when(assignments.findOpenParcelIds()).thenReturn(List.of(PARCEL, OTHER_PARCEL));

        assertThat(lookup.parcelsWithOpenAssignment()).containsExactlyInAnyOrder(PARCEL, OTHER_PARCEL);
    }

    @Test
    void noOpenAssignmentsGivesAnEmptySet() {
        when(assignments.findOpenParcelIds()).thenReturn(List.of());

        assertThat(lookup.parcelsWithOpenAssignment()).isEmpty();
    }

    @Test
    void aCourierOwnsOnlyTheParcelsAssignedToThem() {
        when(assignments.existsByParcelIdAndCourier_UserIdAndEndedAtIsNull(PARCEL, COURIER_USER)).thenReturn(true);
        when(assignments.existsByParcelIdAndCourier_UserIdAndEndedAtIsNull(OTHER_PARCEL, COURIER_USER)).thenReturn(false);

        assertThat(lookup.isAssignedTo(PARCEL, COURIER_USER)).isTrue();
        assertThat(lookup.isAssignedTo(OTHER_PARCEL, COURIER_USER)).isFalse();
    }

    @Test
    void listsTheCouriersOpenParcels() {
        when(assignments.findOpenParcelIdsByCourierUserId(COURIER_USER)).thenReturn(List.of(PARCEL));

        assertThat(lookup.openParcelIds(COURIER_USER)).containsExactly(PARCEL);
    }

    @Test
    void findsTheCourierProfileOfAnAccount() {
        Courier courier = new Courier();
        UUID courierId = UUID.randomUUID();
        courier.setId(courierId);
        when(couriers.findByUserId(COURIER_USER)).thenReturn(Optional.of(courier));
        when(couriers.findByUserId(PARCEL)).thenReturn(Optional.empty());

        assertThat(lookup.findCourierId(COURIER_USER)).contains(courierId);
        assertThat(lookup.findCourierId(PARCEL)).isEmpty();
    }
}

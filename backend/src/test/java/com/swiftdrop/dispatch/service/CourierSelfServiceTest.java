package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.dispatch.api.CourierProfile;
import com.swiftdrop.dispatch.api.CourierStatus;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.ServiceAreaRef;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourierSelfServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    @Mock private DispatchApi dispatchApi;
    @Mock private CourierService courierService;
    @InjectMocks private CourierSelfService service;

    private static CourierResponse courier(CourierAvailability availability) {
        UUID zone = UUID.randomUUID();
        return new CourierResponse(COURIER, USER, "Ramesh Perera", "ramesh@example.com", "+94771234567",
                "Motorbike", "WP CAB 1234", availability, true, AccountStatus.ACTIVE, Set.of(zone),
                List.of(new ServiceAreaRef(zone, "Colombo 03")), CourierWorkload.of(2, 1, 4), Instant.now());
    }

    @Test
    void theProfileComesFromTheCouriersOwnRecord() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.of(COURIER));
        when(courierService.get(COURIER)).thenReturn(courier(CourierAvailability.ON_BREAK));

        CourierProfile profile = service.profile(USER).orElseThrow();

        assertThat(profile.courierId()).isEqualTo(COURIER);
        assertThat(profile.fullName()).isEqualTo("Ramesh Perera");
        assertThat(profile.vehicleRegistration()).isEqualTo("WP CAB 1234");
        assertThat(profile.status()).isEqualTo(CourierStatus.ON_BREAK);
        assertThat(profile.active()).isTrue();
        assertThat(profile.serviceAreas()).containsExactly("Colombo 03");
        assertThat(profile.assigned()).isEqualTo(2);
        assertThat(profile.inProgress()).isEqualTo(1);
        assertThat(profile.completedToday()).isEqualTo(4);
    }

    @Test
    void anAccountWithoutACourierProfileHasNone() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.empty());

        assertThat(service.profile(USER)).isEmpty();
        assertThat(service.changeStatus(USER, CourierStatus.ONLINE)).isEmpty();
        verify(courierService, never()).changeAvailability(any(), any());
    }

    @Test
    void changingTheStatusUsesDispatchsOwnRules() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.of(COURIER));
        when(courierService.changeAvailability(COURIER, CourierAvailability.ONLINE))
                .thenReturn(courier(CourierAvailability.ONLINE));

        assertThat(service.changeStatus(USER, CourierStatus.ONLINE).orElseThrow().status())
                .isEqualTo(CourierStatus.ONLINE);
    }

    @Test
    void aDeactivatedCourierCannotGoOnline() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.of(COURIER));
        when(courierService.changeAvailability(COURIER, CourierAvailability.ONLINE))
                .thenThrow(new ConflictException("This courier is deactivated and cannot go online"));

        assertThatThrownBy(() -> service.changeStatus(USER, CourierStatus.ONLINE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void everyStatusMapsToAnAvailability() {
        for (CourierStatus status : CourierStatus.values()) {
            assertThat(CourierAvailability.valueOf(status.name())).isNotNull();
        }
        for (CourierAvailability availability : CourierAvailability.values()) {
            assertThat(CourierStatus.valueOf(availability.name())).isNotNull();
        }
    }
}

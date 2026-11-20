package com.swiftdrop.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.api.CourierProfile;
import com.swiftdrop.dispatch.api.CourierSelfApi;
import com.swiftdrop.dispatch.api.CourierStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourierProfileServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private CourierSelfApi courierSelfApi;
    @InjectMocks private CourierProfileService service;

    private static CourierProfile profile(CourierStatus status) {
        return new CourierProfile(UUID.randomUUID(), "Ramesh Perera", "ramesh@example.com", "+94771234567",
                "Motorbike", "WP CAB 1234", status, true, List.of("Colombo 03"), 1, 2, 3);
    }

    @Test
    void returnsTheCouriersOwnProfile() {
        CourierProfile mine = profile(CourierStatus.ONLINE);
        when(courierSelfApi.profile(USER)).thenReturn(Optional.of(mine));

        assertThat(service.profile(USER)).isSameAs(mine);
    }

    @Test
    void anAccountWithoutACourierProfileIsNotFound() {
        when(courierSelfApi.profile(USER)).thenReturn(Optional.empty());
        when(courierSelfApi.changeStatus(USER, CourierStatus.OFFLINE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(USER))
                .isInstanceOf(NotFoundException.class).hasMessage(CourierProfileService.NO_PROFILE);
        assertThatThrownBy(() -> service.changeStatus(USER, CourierStatus.OFFLINE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changesTheStatusAndPassesOnDispatchsRefusal() {
        when(courierSelfApi.changeStatus(USER, CourierStatus.ON_BREAK))
                .thenReturn(Optional.of(profile(CourierStatus.ON_BREAK)));
        when(courierSelfApi.changeStatus(USER, CourierStatus.ONLINE))
                .thenThrow(new ConflictException("This courier is deactivated and cannot go online"));

        assertThat(service.changeStatus(USER, CourierStatus.ON_BREAK).status()).isEqualTo(CourierStatus.ON_BREAK);
        assertThatThrownBy(() -> service.changeStatus(USER, CourierStatus.ONLINE))
                .isInstanceOf(ConflictException.class);
    }
}

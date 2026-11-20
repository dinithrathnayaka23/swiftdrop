package com.swiftdrop.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.ChangeCourierStatusRequest;
import com.swiftdrop.delivery.service.CourierProfileService;
import com.swiftdrop.dispatch.api.CourierProfile;
import com.swiftdrop.dispatch.api.CourierStatus;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CourierProfileControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final AuthenticatedUser COURIER = new AuthenticatedUser(USER.toString(), Set.of(Role.COURIER));

    @Mock private CourierProfileService service;
    @InjectMocks private CourierProfileController controller;

    private static CourierProfile profile(CourierStatus status) {
        return new CourierProfile(UUID.randomUUID(), "Ramesh Perera", "ramesh@example.com", "+94771234567",
                "Motorbike", "WP CAB 1234", status, true, List.of(), 0, 0, 0);
    }

    @Test
    void onlyCouriersMayUseIt() {
        assertThat(CourierProfileController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('COURIER')");
        assertThat(CourierProfileController.PATH).isEqualTo("/api/v1/courier/profile");
    }

    @Test
    void readsAndChangesTheSignedInCouriersOwnProfile() {
        CourierProfile online = profile(CourierStatus.ONLINE);
        CourierProfile onBreak = profile(CourierStatus.ON_BREAK);
        when(service.profile(USER)).thenReturn(online);
        when(service.changeStatus(USER, CourierStatus.ON_BREAK)).thenReturn(onBreak);

        assertThat(controller.profile(COURIER)).isSameAs(online);
        assertThat(controller.changeStatus(COURIER, new ChangeCourierStatusRequest(CourierStatus.ON_BREAK)))
                .isSameAs(onBreak);
    }

    @Test
    void theStatusIsRequired() {
        var violations = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new ChangeCourierStatusRequest(null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("status");
    }
}

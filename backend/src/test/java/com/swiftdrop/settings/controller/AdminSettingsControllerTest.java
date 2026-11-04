package com.swiftdrop.settings.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.dto.request.UpdateSystemSettingsRequest;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminSettingsControllerTest {

    @Mock private SystemSettingsService settingsService;
    @InjectMocks private AdminSettingsController controller;

    @Test
    void onlyTheAdminMayUseIt() {
        assertThat(AdminSettingsController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void returnsTheCurrentSettings() {
        SystemSettingsView view = Mockito.mock(SystemSettingsView.class);
        when(settingsService.forAdmin()).thenReturn(view);

        assertThat(controller.get()).isSameAs(view);
    }

    @Test
    void savesAsTheSignedInAdmin() {
        UUID adminId = UUID.randomUUID();
        UpdateSystemSettingsRequest request = Mockito.mock(UpdateSystemSettingsRequest.class);

        controller.update(request, new AuthenticatedUser(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN)));

        verify(settingsService).update(request, adminId);
    }
}

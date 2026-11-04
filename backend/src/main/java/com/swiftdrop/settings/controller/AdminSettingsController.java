package com.swiftdrop.settings.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.dto.request.UpdateSystemSettingsRequest;
import com.swiftdrop.settings.service.SystemSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin: System Settings. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/settings")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public SystemSettingsView get() {
        return settingsService.forAdmin();
    }

    /** Saves the whole form. 409 if the settings changed since they were loaded (see {@code version}). */
    @PutMapping
    public SystemSettingsView update(@Valid @RequestBody UpdateSystemSettingsRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser admin) {
        return settingsService.update(request, UUID.fromString(admin.userId()));
    }
}

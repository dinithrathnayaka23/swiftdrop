package com.swiftdrop.settings.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.settings.dto.response.PublicSettingsResponse;
import com.swiftdrop.settings.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings the web app needs before anyone signs in (contact details, formats, maintenance). */
@RestController
@RequestMapping(ApiPaths.PUBLIC_SETTINGS)
@RequiredArgsConstructor
public class PublicSettingsController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public PublicSettingsResponse get() {
        return settingsService.publicSettings();
    }
}

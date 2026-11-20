package com.swiftdrop.dispatch.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.dispatch.dto.response.DevSampleDeliveryResponse;
import com.swiftdrop.dispatch.service.DevCourierDemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev profile only (Member 7): the courier gives themselves a sample delivery to try the courier
 * app. Not registered outside the dev profile.
 */
@Tag(name = "Dev: courier demo", description = "Dev profile only. A sample delivery for the signed-in courier")
@Profile("dev")
@RestController
@RequestMapping(ApiPaths.API_V1 + "/courier/dev")
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class DevCourierDemoController {

    private final DevCourierDemoService demoService;

    @Operation(summary = "Book a sample parcel and assign it to the signed-in courier (sets them Online)")
    @PostMapping("/sample-delivery")
    @ResponseStatus(HttpStatus.CREATED)
    public DevSampleDeliveryResponse sampleDelivery(@AuthenticationPrincipal AuthenticatedUser courier) {
        return demoService.createSampleDelivery(UUID.fromString(courier.userId()));
    }
}

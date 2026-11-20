package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.request.ChangeCourierStatusRequest;
import com.swiftdrop.delivery.service.CourierProfileService;
import com.swiftdrop.dispatch.api.CourierProfile;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The courier app's profile card: the signed-in courier's own profile and availability. */
@RestController
@RequestMapping(CourierProfileController.PATH)
@PreAuthorize("hasRole('COURIER')")
@RequiredArgsConstructor
public class CourierProfileController {

    static final String PATH = ApiPaths.API_V1 + "/courier/profile";

    private final CourierProfileService courierProfileService;

    /** 404 when the account has no courier profile. */
    @GetMapping
    public CourierProfile profile(@AuthenticationPrincipal AuthenticatedUser courier) {
        return courierProfileService.profile(id(courier));
    }

    /**
     * Online, On Break or Offline; returns the updated profile. 409 when a deactivated courier asks
     * to go online. Going on break or offline keeps the courier's open deliveries.
     */
    @PatchMapping("/status")
    public CourierProfile changeStatus(@AuthenticationPrincipal AuthenticatedUser courier,
                                       @Valid @RequestBody ChangeCourierStatusRequest request) {
        return courierProfileService.changeStatus(id(courier), request.status());
    }

    private static UUID id(AuthenticatedUser user) {
        return UUID.fromString(user.userId());
    }
}

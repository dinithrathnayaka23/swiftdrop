package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.tracking.dto.response.DevSampleParcelResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.service.DevTrackingDemoService;
import com.swiftdrop.tracking.service.PublicTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only demo helper: creates a sample parcel and moves its status, so the tracking screens can
 * be shown before dispatch and the courier app exist. Not registered outside the dev profile.
 */
@Tag(name = "Dev: tracking demo", description = "Dev profile only. Sample parcels and status changes for demos")
@Profile("dev")
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/dev/tracking")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class DevTrackingController {

    private final DevTrackingDemoService demoService;
    private final PublicTrackingService trackingService;

    @Operation(summary = "Create a sample parcel (status CREATED), booked as the signed-in admin")
    @PostMapping("/sample-parcel")
    public ResponseEntity<DevSampleParcelResponse> createSampleParcel(@AuthenticationPrincipal AuthenticatedUser user) {
        String trackingNumber = demoService.createSampleParcel(UUID.fromString(user.userId())).trackingNumber();
        return ResponseEntity.created(URI.create(ApiPaths.PUBLIC_TRACKING + "/" + trackingNumber))
                .body(new DevSampleParcelResponse(trackingNumber));
    }

    @Operation(summary = "Move a parcel to its next normal status, or fail it with ?to=FAILED_ATTEMPT / ?to=FAILED",
            description = "Without `to`: the next normal status (a FAILED_ATTEMPT parcel is retried to CREATED). "
                    + "?to=FAILED_ATTEMPT records a failed attempt; ?to=FAILED closes a FAILED_ATTEMPT parcel, or "
                    + "records the attempt and then closes (two events) for a parcel still on the way. "
                    + "409 when the parcel is DELIVERED or FAILED already. Returns the new public tracking.")
    @PostMapping("/{trackingNumber}/advance")
    public PublicTrackingResponse advance(@PathVariable String trackingNumber,
                                          @RequestParam(required = false) ParcelStatusCode to,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        demoService.advance(trackingNumber, to, UUID.fromString(user.userId()));
        return trackingService.track(trackingNumber);
    }
}

package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.tracking.dto.response.LiveDeliveryResponse;
import com.swiftdrop.tracking.service.AdminLiveDeliveriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin Deliveries Tracking board: the parcels on their way and the status counts. */
@Tag(name = "Admin: Deliveries Tracking", description = "The live board of parcels on their way")
@RestController
@RequestMapping(AdminLiveTrackingController.BASE_PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminLiveTrackingController {

    public static final String BASE_PATH = ApiPaths.API_V1 + "/admin/live";

    private final AdminLiveDeliveriesService deliveriesService;

    @Operation(summary = "Parcels on their way (picked up, in transit, out for delivery)",
            description = "Most recently updated first. `status` narrows to one of those; `search` matches part "
                    + "of a tracking number.")
    @GetMapping("/deliveries")
    public PagedModel<LiveDeliveryResponse> deliveries(
            @RequestParam(required = false) ParcelStatusCode status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return new PagedModel<>(deliveriesService.list(status, search, page, size));
    }

    @Operation(summary = "Parcels per status", description = "Every status is present, 0 when there are none.")
    @GetMapping("/counts")
    public Map<ParcelStatusCode, Long> counts() {
        return deliveriesService.counts();
    }
}

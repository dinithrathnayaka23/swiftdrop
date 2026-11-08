package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.tracking.dto.response.LiveDeliveryDetailResponse;
import com.swiftdrop.tracking.dto.response.LiveDeliveryResponse;
import com.swiftdrop.tracking.service.AdminLiveDeliveriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin Deliveries Tracking: the table, the status counts and one delivery's details. */
@Tag(name = "Admin: Deliveries Tracking", description = "Every delivery with its live status")
@RestController
@RequestMapping(AdminLiveTrackingController.BASE_PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminLiveTrackingController {

    public static final String BASE_PATH = ApiPaths.API_V1 + "/admin/live";

    private final AdminLiveDeliveriesService deliveriesService;

    @Operation(summary = "Deliveries, most recently changed first",
            description = "`search` matches part of a tracking number, recipient or merchant name. "
                    + "`bookedFrom`/`bookedTo` are yyyy-MM-dd, whole days in the System Settings time zone.")
    @GetMapping("/deliveries")
    public PagedModel<LiveDeliveryResponse> deliveries(
            @RequestParam(required = false) ParcelStatusCode status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var filter = new AdminLiveDeliveriesService.Filter(status, search, bookedFrom, bookedTo);
        return new PagedModel<>(deliveriesService.list(filter, page, size));
    }

    @Operation(summary = "One delivery, for its detail page")
    @GetMapping("/deliveries/{trackingNumber}")
    public LiveDeliveryDetailResponse delivery(@PathVariable String trackingNumber) {
        return deliveriesService.get(trackingNumber);
    }

    @Operation(summary = "Parcels per status", description = "Every status is present, 0 when there are none.")
    @GetMapping("/counts")
    public Map<ParcelStatusCode, Long> counts() {
        return deliveriesService.counts();
    }
}

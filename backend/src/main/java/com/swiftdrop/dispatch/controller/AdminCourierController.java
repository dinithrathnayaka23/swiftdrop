package com.swiftdrop.dispatch.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.dispatch.dto.request.ChangeAvailabilityRequest;
import com.swiftdrop.dispatch.dto.request.CourierFilter;
import com.swiftdrop.dispatch.dto.request.CreateCourierRequest;
import com.swiftdrop.dispatch.dto.request.UpdateCourierRequest;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.service.CourierService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin Couriers screen: the list, the cards above it, the detail drawer and Add Courier. */
@RestController
@RequestMapping(AdminCourierController.PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminCourierController {

    static final String PATH = ApiPaths.API_V1 + "/admin/couriers";
    static final int MAX_PAGE_SIZE = 100;

    private final CourierService courierService;

    /** Couriers, newest first, filtered by text, availability, service area, vehicle type and active. */
    @GetMapping
    public PagedModel<CourierResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CourierAvailability availability,
            @RequestParam(required = false) UUID serviceAreaId,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        CourierFilter filter = new CourierFilter(search, availability, serviceAreaId, vehicleType, active);
        return new PagedModel<>(courierService.list(filter, page, size));
    }

    /** Total, online, on break and offline couriers. */
    @GetMapping("/summary")
    public CourierSummaryResponse summary() {
        return courierService.summary();
    }

    @GetMapping("/{courierId}")
    public CourierResponse get(@PathVariable UUID courierId) {
        return courierService.get(courierId);
    }

    /** Adds a courier with a sign-in account; they start offline. 409 if the email or vehicle is already used. */
    @PostMapping
    public ResponseEntity<CourierResponse> create(@Valid @RequestBody CreateCourierRequest request) {
        CourierResponse created = courierService.create(request);
        return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
    }

    /** Changes the vehicle and/or service areas; fields left out stay as they are. */
    @PatchMapping("/{courierId}")
    public CourierResponse update(@PathVariable UUID courierId, @Valid @RequestBody UpdateCourierRequest request) {
        return courierService.update(courierId, request);
    }

    /** Online, on break or offline. */
    @PatchMapping("/{courierId}/availability")
    public CourierResponse changeAvailability(@PathVariable UUID courierId,
                                              @Valid @RequestBody ChangeAvailabilityRequest request) {
        return courierService.changeAvailability(courierId, request.availability());
    }
}

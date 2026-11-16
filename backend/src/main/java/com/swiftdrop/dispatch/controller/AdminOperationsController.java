package com.swiftdrop.dispatch.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.dispatch.dto.response.ActiveDeliveryResponse;
import com.swiftdrop.dispatch.dto.response.OverviewResponse;
import com.swiftdrop.dispatch.service.OperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin Operations Overview and its Active Deliveries table. */
@RestController
@RequestMapping(AdminOperationsController.PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminOperationsController {

    static final String PATH = ApiPaths.API_V1 + "/admin/dispatch";

    private final OperationsService operationsService;

    /** Deliveries under way (open IN_PROGRESS assignments), newest first, {@code size} 1-50. */
    @GetMapping("/active-deliveries")
    public PagedModel<ActiveDeliveryResponse> activeDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminFailedDeliveryController.checkPage(page, size);
        return new PagedModel<>(operationsService.activeDeliveries(page, size));
    }

    /** Today's numbers (Sri Lanka time) and the five busiest couriers. */
    @GetMapping("/overview")
    public OverviewResponse overview() {
        return operationsService.overview();
    }
}

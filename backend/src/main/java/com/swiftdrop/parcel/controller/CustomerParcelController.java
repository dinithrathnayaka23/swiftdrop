package com.swiftdrop.parcel.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.parcel.dto.request.CustomerParcelFilter;
import com.swiftdrop.parcel.dto.response.CustomerParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.CustomerParcelSummaryResponse;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelViewService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own parcels: dashboard counts, My Deliveries and Delivery Details. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/customer/parcels")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerParcelController {

    private final ParcelViewService parcelViewService;

    /** Number of the customer's parcels per status (every status, 0 when none). */
    @GetMapping("/counts")
    public Map<ParcelStatusCode, Long> counts(@AuthenticationPrincipal AuthenticatedUser customer) {
        return parcelViewService.customerCounts(id(customer));
    }

    /** The customer's parcels, newest first, filtered by search text, status and booking dates. */
    @GetMapping
    public PagedModel<CustomerParcelSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParcelStatusCode status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams.check(page, size);
        return new PagedModel<>(parcelViewService.listForCustomer(id(customer),
                new CustomerParcelFilter(search, status, bookedFrom, bookedTo), page, size));
    }

    /** One of the customer's parcels; 404 when it doesn't exist or isn't theirs. */
    @GetMapping("/by-tracking-number/{trackingNumber}")
    public CustomerParcelDetailResponse get(@AuthenticationPrincipal AuthenticatedUser customer,
                                            @PathVariable String trackingNumber) {
        return parcelViewService.getForCustomer(id(customer), trackingNumber);
    }

    private static UUID id(AuthenticatedUser user) {
        return UUID.fromString(user.userId());
    }
}

package com.swiftdrop.parcel.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.parcel.dto.request.AdminParcelFilter;
import com.swiftdrop.parcel.dto.request.AdminParcelTab;
import com.swiftdrop.parcel.dto.response.AdminParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.AdminParcelRowResponse;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelViewService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every parcel, for dispatchers/admins: the Parcels screen. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/parcels")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminParcelController {

    private final ParcelViewService parcelViewService;

    /** Number of parcels in each tab of the Parcels screen. */
    @GetMapping("/counts")
    public Map<AdminParcelTab, Long> counts() {
        return parcelViewService.adminCounts();
    }

    /** Parcels, newest first, filtered by tab, search text, status, delivery city and creation dates. */
    @GetMapping
    public PagedModel<AdminParcelRowResponse> list(
            @RequestParam(defaultValue = "ALL") AdminParcelTab tab,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParcelStatusCode status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams.check(page, size);
        return new PagedModel<>(parcelViewService.listForAdmin(
                new AdminParcelFilter(tab, search, status, area, createdFrom, createdTo), page, size));
    }

    @GetMapping("/{parcelId}")
    public AdminParcelDetailResponse get(@PathVariable UUID parcelId) {
        return parcelViewService.getForAdmin(parcelId);
    }
}

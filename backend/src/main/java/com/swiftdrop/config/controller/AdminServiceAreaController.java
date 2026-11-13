package com.swiftdrop.config.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.dto.request.UpdateServiceAreaRequest;
import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.dto.response.ServiceAreaResponse;
import com.swiftdrop.config.service.ServiceAreaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin Service Areas screen: delivery zones and the places they cover. */
@RestController
@RequestMapping(AdminServiceAreaController.PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminServiceAreaController {

    static final String PATH = ApiPaths.API_V1 + "/admin/service-areas";
    static final int MAX_PAGE_SIZE = 100;

    private final ServiceAreaService serviceAreaService;

    /** Zones sorted by name, filtered by text (name, city, district, postal code) and active. */
    @GetMapping
    public PagedModel<ServiceAreaResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return new PagedModel<>(serviceAreaService.list(search, active, page, size));
    }

    @GetMapping("/{zoneId}")
    public ServiceAreaDetailResponse get(@PathVariable UUID zoneId) {
        return serviceAreaService.get(zoneId);
    }

    /** Adds a zone with its coverage. 201 + Location. 409 if the name is taken or a place is already covered. */
    @PostMapping
    public ResponseEntity<ServiceAreaDetailResponse> create(@Valid @RequestBody CreateServiceAreaRequest request) {
        ServiceAreaDetailResponse created = serviceAreaService.create(request);
        return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
    }

    /** Renames the zone and/or changes its description. */
    @PatchMapping("/{zoneId}")
    public ServiceAreaDetailResponse update(@PathVariable UUID zoneId,
                                            @Valid @RequestBody UpdateServiceAreaRequest request) {
        return serviceAreaService.update(zoneId, request);
    }

    /** 409 if another active zone covers one of its places. */
    @PostMapping("/{zoneId}/activate")
    public ServiceAreaDetailResponse activate(@PathVariable UUID zoneId) {
        return serviceAreaService.activate(zoneId);
    }

    /** Always allowed; zones are never deleted. */
    @PostMapping("/{zoneId}/deactivate")
    public ServiceAreaDetailResponse deactivate(@PathVariable UUID zoneId) {
        return serviceAreaService.deactivate(zoneId);
    }

    /** Adds a place. 201 + Location of the zone (coverage rows have no address of their own). */
    @PostMapping("/{zoneId}/coverage-areas")
    public ResponseEntity<CoverageAreaResponse> addCoverage(@PathVariable UUID zoneId,
                                                            @Valid @RequestBody CoverageAreaRequest request) {
        CoverageAreaResponse created = serviceAreaService.addCoverage(zoneId, request);
        return ResponseEntity.created(URI.create(PATH + "/" + zoneId)).body(created);
    }

    @DeleteMapping("/{zoneId}/coverage-areas/{coverageId}")
    public ResponseEntity<Void> removeCoverage(@PathVariable UUID zoneId, @PathVariable UUID coverageId) {
        serviceAreaService.removeCoverage(zoneId, coverageId);
        return ResponseEntity.noContent().build();
    }
}

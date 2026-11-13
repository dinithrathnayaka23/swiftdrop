package com.swiftdrop.dispatch.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.dispatch.dto.request.AssignParcelRequest;
import com.swiftdrop.dispatch.dto.request.BulkAssignRequest;
import com.swiftdrop.dispatch.dto.request.ReassignParcelRequest;
import com.swiftdrop.dispatch.dto.request.UnassignParcelRequest;
import com.swiftdrop.dispatch.dto.response.AssignmentResponse;
import com.swiftdrop.dispatch.dto.response.BulkAssignResponse;
import com.swiftdrop.dispatch.service.AssignmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assigning parcels to couriers from the admin screens (Unassigned Parcels, parcel detail, Couriers).
 * Assigning does not change the parcel's status.
 */
@RestController
@RequestMapping(AdminDispatchController.PATH)
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminDispatchController {

    static final String PATH = ApiPaths.API_V1 + "/admin/dispatch";

    private final AssignmentService assignmentService;

    /** Gives a waiting (CREATED) parcel to an online courier. 201 + Location. */
    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> assign(@Valid @RequestBody AssignParcelRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser admin) {
        AssignmentResponse created = assignmentService.assign(request.parcelId(), request.courierId(), idOf(admin));
        return ResponseEntity.created(URI.create(PATH + "/assignments/" + created.id())).body(created);
    }

    /** Gives up to 50 parcels to one courier. 200 with the assigned and failed parcels, even if all failed. */
    @PostMapping("/assignments/bulk")
    public BulkAssignResponse assignAll(@Valid @RequestBody BulkAssignRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser admin) {
        return assignmentService.assignAll(request.parcelIds(), request.courierId(), idOf(admin));
    }

    @GetMapping("/assignments/{assignmentId}")
    public AssignmentResponse get(@PathVariable UUID assignmentId) {
        return assignmentService.get(assignmentId);
    }

    /** Moves the parcel to another courier; returns the new assignment. */
    @PostMapping("/parcels/{parcelId}/reassign")
    public AssignmentResponse reassign(@PathVariable UUID parcelId, @Valid @RequestBody ReassignParcelRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser admin) {
        return assignmentService.reassign(parcelId, request.courierId(), request.reason(), idOf(admin));
    }

    /** Takes the parcel back from its courier before pickup; returns the ended assignment. */
    @PostMapping("/parcels/{parcelId}/unassign")
    public AssignmentResponse unassign(@PathVariable UUID parcelId,
                                       @Valid @RequestBody(required = false) UnassignParcelRequest request) {
        return assignmentService.unassign(parcelId, request == null ? null : request.reason());
    }

    private static UUID idOf(AuthenticatedUser admin) {
        return UUID.fromString(admin.userId());
    }
}

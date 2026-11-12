package com.swiftdrop.dispatch.service;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.dto.response.AssignmentResponse;
import com.swiftdrop.dispatch.dto.response.BulkAssignResponse;
import com.swiftdrop.dispatch.mapper.AssignmentMapper;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Gives parcels to couriers, moves them to another courier and takes them back.
 *
 * <p>Assigning does <b>not</b> change the parcel's status: it stays CREATED until the courier
 * picks it up. An assignment is open while {@code ended_at} is null; the database allows one open
 * assignment per parcel ({@code ux_delivery_assignments_open_per_parcel}). Assignments that end
 * because of a parcel status change (delivered, failed) are closed by
 * {@link AssignmentLifecycleService}, not here.
 */
@Slf4j
@Service
public class AssignmentService {

    /** A reassigned parcel the old courier already has starts IN_PROGRESS with the new one. */
    static final Set<ParcelStatusCode> WITH_COURIER =
            Set.of(ParcelStatusCode.PICKED_UP, ParcelStatusCode.IN_TRANSIT, ParcelStatusCode.OUT_FOR_DELIVERY);

    static final String ALREADY_ASSIGNED = "This parcel is already assigned to a courier";

    private final DeliveryAssignmentRepository assignmentRepository;
    private final CourierRepository courierRepository;
    private final ParcelService parcelService;
    private final UserApi userApi;
    /** One transaction per parcel in a bulk assignment, so one failure doesn't undo the others. */
    private final TransactionTemplate perParcel;

    public AssignmentService(DeliveryAssignmentRepository assignmentRepository, CourierRepository courierRepository,
                             ParcelService parcelService, UserApi userApi,
                             PlatformTransactionManager transactionManager) {
        this.assignmentRepository = assignmentRepository;
        this.courierRepository = courierRepository;
        this.parcelService = parcelService;
        this.userApi = userApi;
        this.perParcel = new TransactionTemplate(transactionManager);
        this.perParcel.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- Assign ------------------------------------------------------------------------------

    /**
     * Gives a CREATED parcel without a courier to an online courier.
     *
     * @throws NotFoundException if the parcel or the courier does not exist
     * @throws ConflictException if the parcel is not waiting for a courier, already has one, or the courier can't take it
     */
    @Transactional
    public AssignmentResponse assign(UUID parcelId, UUID courierId, UUID assignedByUserId) {
        return doAssign(parcelId, courierId, assignedByUserId);
    }

    /**
     * Gives each parcel to the courier in its own transaction. A parcel that can't be assigned is
     * reported in {@code failed} with the reason; the others are still assigned. Duplicate ids are
     * handled once.
     */
    public BulkAssignResponse assignAll(Collection<UUID> parcelIds, UUID courierId, UUID assignedByUserId) {
        List<AssignmentResponse> assigned = new ArrayList<>();
        List<BulkAssignResponse.Failure> failed = new ArrayList<>();
        for (UUID parcelId : new LinkedHashSet<>(parcelIds)) {
            try {
                assigned.add(perParcel.execute(status -> doAssign(parcelId, courierId, assignedByUserId)));
            } catch (ApiException ex) {
                failed.add(new BulkAssignResponse.Failure(parcelId, ex.getMessage()));
            } catch (RuntimeException ex) {
                // Unexpected (e.g. the database): report it for this parcel and carry on with the rest.
                log.error("Bulk assignment of parcel {} to courier {} failed", parcelId, courierId, ex);
                failed.add(new BulkAssignResponse.Failure(parcelId, "The parcel could not be assigned"));
            }
        }
        log.info("Bulk assignment to courier {}: {} assigned, {} failed", courierId, assigned.size(), failed.size());
        return new BulkAssignResponse(assigned, failed);
    }

    private AssignmentResponse doAssign(UUID parcelId, UUID courierId, UUID assignedByUserId) {
        ParcelDetails parcel = parcelService.getParcel(parcelId);
        if (parcel.currentStatus() != ParcelStatusCode.CREATED) {
            throw new ConflictException("Only a parcel that has not been picked up yet can be assigned");
        }
        if (assignmentRepository.existsByParcelIdAndEndedAtIsNull(parcelId)) {
            throw new ConflictException(ALREADY_ASSIGNED);
        }
        Courier courier = assignableCourier(courierId);
        DeliveryAssignment saved = insert(newAssignment(parcelId, courier, assignedByUserId, AssignmentStatus.ASSIGNED));
        log.info("Parcel {} assigned to courier {}", parcelId, courierId);
        return AssignmentMapper.toResponse(saved);
    }

    // ---- Reassign and unassign ---------------------------------------------------------------

    /**
     * Ends the parcel's open assignment as REASSIGNED and gives the parcel to another courier, in
     * one transaction. If the old courier already has the parcel, the new assignment starts IN_PROGRESS.
     *
     * @throws NotFoundException if the parcel or the courier does not exist
     * @throws ConflictException if the parcel is delivered or failed, has no courier, is already with
     *                           this courier, or the courier can't take it
     */
    @Transactional
    public AssignmentResponse reassign(UUID parcelId, UUID courierId, String reason, UUID assignedByUserId) {
        ParcelDetails parcel = parcelService.getParcel(parcelId);
        if (parcel.currentStatus().isTerminal()) {
            throw new ConflictException("A delivered or failed parcel cannot be reassigned");
        }
        DeliveryAssignment current = assignmentRepository.findOpenByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ConflictException("This parcel has no courier yet: assign it instead"));
        if (current.getCourier().getId().equals(courierId)) {
            throw new ConflictException("This parcel is already assigned to this courier");
        }
        Courier courier = assignableCourier(courierId);

        end(current, AssignmentStatus.REASSIGNED, reason.trim());
        assignmentRepository.saveAndFlush(current); // ended before the new row, for the one-open-per-parcel index

        AssignmentStatus startAs = WITH_COURIER.contains(parcel.currentStatus())
                ? AssignmentStatus.IN_PROGRESS : AssignmentStatus.ASSIGNED;
        DeliveryAssignment saved = insert(newAssignment(parcelId, courier, assignedByUserId, startAs));
        log.info("Parcel {} reassigned from courier {} to courier {}", parcelId, current.getCourier().getId(),
                courierId);
        return AssignmentMapper.toResponse(saved);
    }

    /**
     * Takes a parcel back from its courier before pickup: the open assignment ends as CANCELLED and
     * the parcel is waiting for a courier again.
     *
     * @throws NotFoundException if the parcel does not exist
     * @throws ConflictException if the parcel was already picked up or has no courier
     */
    @Transactional
    public AssignmentResponse unassign(UUID parcelId, String reason) {
        ParcelDetails parcel = parcelService.getParcel(parcelId);
        if (parcel.currentStatus() != ParcelStatusCode.CREATED) {
            throw new ConflictException("Only a parcel that has not been picked up yet can be unassigned");
        }
        DeliveryAssignment current = assignmentRepository.findOpenByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ConflictException("This parcel is not assigned to a courier"));
        end(current, AssignmentStatus.CANCELLED, StringUtils.hasText(reason) ? reason.trim() : null);
        DeliveryAssignment saved = assignmentRepository.saveAndFlush(current);
        log.info("Parcel {} unassigned from courier {}", parcelId, current.getCourier().getId());
        return AssignmentMapper.toResponse(saved);
    }

    // ---- Read --------------------------------------------------------------------------------

    /** @throws NotFoundException if there is no such assignment */
    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId).map(AssignmentMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Assignment not found"));
    }

    // ---- Helpers -----------------------------------------------------------------------------

    /**
     * The courier, if they may be given parcels now: active, online, and their account can sign in.
     *
     * @throws NotFoundException if there is no such courier
     * @throws ConflictException if the courier can't take parcels
     */
    private Courier assignableCourier(UUID courierId) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new NotFoundException("Courier not found"));
        if (!courier.isActive()) {
            throw new ConflictException("This courier is deactivated and cannot be given parcels");
        }
        if (courier.getAvailabilityStatus() != CourierAvailability.ONLINE) {
            throw new ConflictException(courier.getAvailabilityStatus() == CourierAvailability.ON_BREAK
                    ? "This courier is on a break and cannot be given parcels"
                    : "This courier is offline and cannot be given parcels");
        }
        UserSummary user = userApi.findUser(courier.getUserId())
                .orElseThrow(() -> new IllegalStateException("Courier " + courierId + " has no user account"));
        if (user.accountStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("This courier's account is not active, so they cannot be given parcels");
        }
        // TODO(zone check): refuse a courier whose service areas (courier.getServiceAreaIds()) don't
        //  cover the parcel's drop-off zone (parcel.dropoffAddress().zoneId()). Waiting for the zones backend.
        return courier;
    }

    private static DeliveryAssignment newAssignment(UUID parcelId, Courier courier, UUID assignedByUserId,
                                                    AssignmentStatus status) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setParcelId(parcelId);
        assignment.setCourier(courier);
        assignment.setAssignedByUserId(assignedByUserId);
        assignment.setAssignmentStatus(status);
        return assignment;
    }

    /** Saves a new open assignment; losing the race for the parcel's one open assignment is a 409. */
    private DeliveryAssignment insert(DeliveryAssignment assignment) {
        try {
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(ALREADY_ASSIGNED);
        }
    }

    private static void end(DeliveryAssignment assignment, AssignmentStatus status, String reason) {
        assignment.setAssignmentStatus(status);
        assignment.setEndedAt(endTime(assignment, Instant.now()));
        assignment.setReassignmentReason(reason);
    }

    /** {@code at}, but never before the assignment started (the database checks ended_at >= assigned_at). */
    static Instant endTime(DeliveryAssignment assignment, Instant at) {
        Instant assignedAt = assignment.getAssignedAt();
        return assignedAt != null && at.isBefore(assignedAt) ? assignedAt : at;
    }
}

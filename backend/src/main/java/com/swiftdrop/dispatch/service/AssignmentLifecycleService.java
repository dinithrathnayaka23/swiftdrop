package com.swiftdrop.dispatch.service;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the parcel's open assignment in step with the parcel's status. Dispatch is the only module
 * that ends assignments: the courier app changes the parcel's status, and this closes the assignment.
 *
 * <pre>
 *   PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY   ASSIGNED -> IN_PROGRESS
 *   DELIVERED                                 -> COMPLETED, ended_at set
 *   FAILED                                    -> FAILED, ended_at set
 *   anything else                             ignored
 * </pre>
 *
 * <p>Events arrive at least once and possibly out of order, so this only ever moves an open
 * assignment forward: no open assignment means nothing to do, and a repeated or older event finds
 * the assignment already there or further on. No event-id store is needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentLifecycleService {

    private final DeliveryAssignmentRepository assignmentRepository;

    /**
     * Applies one status event. Incomplete events are skipped with a warning; database errors are
     * thrown on so the message is retried and then dead-lettered.
     */
    @Transactional
    public void apply(ParcelStatusChangedEvent event) {
        if (event == null || event.parcelId() == null || event.newStatus() == null) {
            log.warn("Ignoring an incomplete parcel status event");
            return;
        }
        Optional<AssignmentStatus> target = targetFor(event.newStatus());
        if (target.isEmpty()) {
            log.debug("Parcel {} is now {}: nothing to change on its assignment", event.parcelId(), event.newStatus());
            return;
        }
        Optional<DeliveryAssignment> open = assignmentRepository.findOpenByParcelIdForUpdate(event.parcelId());
        if (open.isEmpty()) {
            log.debug("Parcel {} is now {} but has no open assignment", event.parcelId(), event.newStatus());
            return;
        }
        DeliveryAssignment assignment = open.get();
        AssignmentStatus next = target.get();
        if (next == AssignmentStatus.IN_PROGRESS) {
            if (assignment.getAssignmentStatus() == AssignmentStatus.ASSIGNED) {
                assignment.setAssignmentStatus(AssignmentStatus.IN_PROGRESS);
                log.info("Assignment {} of parcel {} is in progress", assignment.getId(), event.parcelId());
            }
            return;
        }
        Instant at = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        assignment.setAssignmentStatus(next);
        assignment.setEndedAt(AssignmentService.endTime(assignment, at));
        log.info("Assignment {} of parcel {} ended as {}", assignment.getId(), event.parcelId(), next);
    }

    /** The assignment status a parcel status leads to, or empty if it changes nothing (unknown codes too). */
    static Optional<AssignmentStatus> targetFor(String parcelStatus) {
        ParcelStatusCode status;
        try {
            status = ParcelStatusCode.valueOf(parcelStatus);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return switch (status) {
            case PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY -> Optional.of(AssignmentStatus.IN_PROGRESS);
            case DELIVERED -> Optional.of(AssignmentStatus.COMPLETED);
            case FAILED -> Optional.of(AssignmentStatus.FAILED);
            default -> Optional.empty();
        };
    }
}

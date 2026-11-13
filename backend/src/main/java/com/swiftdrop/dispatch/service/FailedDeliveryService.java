package com.swiftdrop.dispatch.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.dto.request.FailedDeliveryScope;
import com.swiftdrop.dispatch.dto.response.DefaultFailedActionResponse;
import com.swiftdrop.dispatch.dto.response.FailedDecisionResponse;
import com.swiftdrop.dispatch.dto.response.FailedDeliveryResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Failed Deliveries queue: parcels whose delivery attempt failed (FAILED_ATTEMPT, waiting for the
 * dispatcher) or that were closed as FAILED, and the dispatcher's decision on them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedDeliveryService {

    /** Largest page: rows read their history one by one (see {@link #list}). */
    public static final int MAX_PAGE_SIZE = 50;

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final UserApi userApi;
    private final SystemSettingsService settingsService;

    /**
     * One page of the queue, newest booking first (the parcel module's order; it can't sort by the
     * time of the failure).
     */
    @Transactional(readOnly = true)
    public Page<FailedDeliveryResponse> list(FailedDeliveryScope scope, int page, int size) {
        ParcelStatusCode status = scope == FailedDeliveryScope.CLOSED
                ? ParcelStatusCode.FAILED : ParcelStatusCode.FAILED_ATTEMPT;
        Page<ParcelDetails> parcels = parcelService.listByStatus(status, PageRequest.of(page, Math.min(size,
                MAX_PAGE_SIZE)));
        if (parcels.isEmpty()) {
            return parcels.map(parcel -> null);
        }
        // Every failed attempt of the page's parcels, with its courier, in one query.
        Map<UUID, List<DeliveryAssignment>> failedAttempts = assignmentRepository
                .findFailedByParcelIds(parcels.getContent().stream().map(ParcelDetails::id).toList()).stream()
                .collect(Collectors.groupingBy(DeliveryAssignment::getParcelId));
        // Names of the couriers shown: one look-up per distinct courier on the page (UserApi has no batch).
        Map<UUID, String> courierNames = failedAttempts.values().stream()
                .map(FailedDeliveryService::latest).flatMap(Optional::stream)
                .map(attempt -> attempt.getCourier().getUserId()).distinct()
                .map(userApi::findUser).flatMap(Optional::stream)
                .collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));

        return parcels.map(parcel -> {
            List<DeliveryAssignment> attempts = failedAttempts.getOrDefault(parcel.id(), List.of());
            Optional<Courier> courier = latest(attempts).map(DeliveryAssignment::getCourier);
            // One history call per row is acceptable ONLY because the page is capped at MAX_PAGE_SIZE (50):
            // the parcel module has no batch read of the latest failure entry.
            Optional<StatusHistoryEntry> failure = latestFailure(parcelStatusService.getHistory(parcel.id()));
            return new FailedDeliveryResponse(
                    parcel.id(),
                    parcel.trackingNumber(),
                    parcel.currentStatus(),
                    failure.map(StatusHistoryEntry::failureReason).orElse(null),
                    failure.map(StatusHistoryEntry::changedAt).orElse(null),
                    courier.map(Courier::getId).orElse(null),
                    courier.map(found -> courierNames.get(found.getUserId())).orElse(null),
                    attempts.size(),
                    parcel.dropoffAddress() == null ? null : parcel.dropoffAddress().city());
        });
    }

    // ---- Decisions ---------------------------------------------------------------------------

    /**
     * Retries a failed attempt: the parcel goes back to CREATED and shows in Unassigned again. It does
     * not assign a courier: the dispatcher does that next with the normal assign call, so a late
     * FAILED_ATTEMPT event can't end the new assignment within this call.
     *
     * @throws NotFoundException if there is no such parcel
     * @throws ConflictException if the parcel is not in FAILED_ATTEMPT
     */
    @Transactional
    public FailedDecisionResponse retry(UUID parcelId, String note, UUID adminUserId) {
        requireFailedAttempt(parcelService.getParcel(parcelId));
        endOpenAssignment(parcelId);
        parcelStatusService.changeStatus(parcelId, StatusChange.to(ParcelStatusCode.CREATED, adminUserId,
                StringUtils.hasText(note) ? note.trim() : null));
        log.info("Failed delivery of parcel {} retried", parcelId);
        return new FailedDecisionResponse(parcelId, ParcelStatusCode.CREATED);
    }

    /**
     * Closes a failed attempt for good: the parcel becomes FAILED (an end state) with the reason.
     *
     * @throws NotFoundException if there is no such parcel
     * @throws ConflictException if the parcel is not in FAILED_ATTEMPT
     */
    @Transactional
    public FailedDecisionResponse close(UUID parcelId, String reason, UUID adminUserId) {
        requireFailedAttempt(parcelService.getParcel(parcelId));
        endOpenAssignment(parcelId);
        parcelStatusService.changeStatus(parcelId, StatusChange.failed(adminUserId, reason.trim(), null));
        log.info("Failed delivery of parcel {} closed", parcelId);
        return new FailedDecisionResponse(parcelId, ParcelStatusCode.FAILED);
    }

    private static void requireFailedAttempt(ParcelDetails parcel) {
        if (parcel.currentStatus() != ParcelStatusCode.FAILED_ATTEMPT) {
            throw new ConflictException("Only a parcel whose delivery attempt failed can be retried or closed");
        }
    }

    /**
     * Ends an assignment still open after the failed attempt (the status listener normally ended it
     * already; this covers a lagging event). Same row lock as reassign and the listener.
     */
    private void endOpenAssignment(UUID parcelId) {
        assignmentRepository.findOpenByParcelIdForUpdate(parcelId).ifPresent(open -> {
            open.setAssignmentStatus(AssignmentStatus.FAILED);
            open.setEndedAt(AssignmentService.endTime(open, Instant.now()));
            log.info("Assignment {} of parcel {} ended before the dispatcher's decision", open.getId(), parcelId);
        });
    }

    // ---- Settings ----------------------------------------------------------------------------

    /** The System Settings default action for a failed delivery. */
    public DefaultFailedActionResponse defaultAction() {
        return new DefaultFailedActionResponse(settingsService.current().failedDeliveryAction());
    }

    /** The most recently ended failed attempt (ended_at, then assigned_at). */
    static Optional<DeliveryAssignment> latest(List<DeliveryAssignment> attempts) {
        return attempts.stream().max(Comparator
                .comparing(DeliveryAssignment::getEndedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DeliveryAssignment::getAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** The latest FAILED_ATTEMPT or FAILED entry of a history (oldest first). */
    static Optional<StatusHistoryEntry> latestFailure(List<StatusHistoryEntry> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ParcelStatusCode status = history.get(i).status();
            if (status == ParcelStatusCode.FAILED_ATTEMPT || status == ParcelStatusCode.FAILED) {
                return Optional.of(history.get(i));
            }
        }
        return Optional.empty();
    }
}

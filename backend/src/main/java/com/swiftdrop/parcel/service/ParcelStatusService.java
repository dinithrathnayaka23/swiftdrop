package com.swiftdrop.parcel.service;

import static com.swiftdrop.parcel.service.Texts.trimToNull;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.mapper.ParcelMapper;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.repository.ParcelStatusRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the parcel module for status changes and status history. This is the only place
 * that changes a parcel's status; the allowed changes are in {@link ParcelStatusCode}.
 *
 * <p>Callers check that the user may change the parcel (for example that the courier is assigned
 * to it) before calling {@link #changeStatus}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelStatusService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusRepository parcelStatusRepository;
    private final ParcelStatusHistoryRepository statusHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Moves the parcel to {@code change.newStatus()}, records the change in the history and, after
     * commit, publishes a {@link ParcelStatusChangedEvent}. Nothing is saved or published when the
     * change is rejected.
     *
     * @return the new history entry
     * @throws NotFoundException   if the parcel does not exist
     * @throws ConflictException   if the parcel's current status cannot change to the new one
     * @throws BadRequestException if a FAILED_ATTEMPT or FAILED change has no reason, a DELIVERED change has no proof,
     *                             or delivery or failure details are sent with another status
     */
    @Transactional
    public StatusHistoryEntry changeStatus(UUID parcelId, StatusChange change) {
        Parcel parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new NotFoundException("Parcel not found"));
        ParcelStatusCode current = ParcelStatusCode.valueOf(parcel.getCurrentStatus().getStatusCode());
        ParcelStatusCode next = change.newStatus();
        if (!current.canChangeTo(next)) {
            throw new ConflictException("A parcel that is %s cannot be changed to %s".formatted(current, next));
        }

        String failureReason = trimToNull(change.failureReason());
        String recipientName = trimToNull(change.recipientName());
        String proofFileUrl = trimToNull(change.proofFileUrl());
        checkDetails(next, failureReason, recipientName, proofFileUrl);

        ParcelStatus nextStatus = parcelStatusRepository.getReferenceById(next.name());
        ParcelStatusHistory entry = new ParcelStatusHistory();
        entry.setParcel(parcel);
        entry.setPreviousStatus(parcel.getCurrentStatus());
        entry.setStatus(nextStatus);
        entry.setChangedByUserId(change.changedByUserId());
        entry.setNote(trimToNull(change.note()));
        entry.setFailureReason(failureReason);
        entry.setRecipientName(recipientName);
        entry.setProofFileUrl(proofFileUrl);
        parcel.setCurrentStatus(nextStatus);
        ParcelStatusHistory saved = statusHistoryRepository.saveAndFlush(entry);

        long statusVersion = statusHistoryRepository.countByParcelId(parcelId);
        eventPublisher.publishEvent(new ParcelStatusChangedEvent(UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE, ParcelStatusChangedEvent.SCHEMA_VERSION, parcel.getId(),
                parcel.getTrackingNumber(), parcel.getMerchantId(), current.name(), next.name(), statusVersion,
                saved.getId(), change.changedByUserId(), saved.getChangedAt()));

        log.info("Parcel {} changed from {} to {}", parcelId, current, next);
        return ParcelMapper.toEntry(saved);
    }

    /**
     * The parcel's status history, oldest first.
     *
     * @throws NotFoundException if the parcel does not exist
     */
    @Transactional(readOnly = true)
    public List<StatusHistoryEntry> getHistory(UUID parcelId) {
        if (!parcelRepository.existsById(parcelId)) {
            throw new NotFoundException("Parcel not found");
        }
        return statusHistoryRepository.findByParcelIdOrderByChangedAtAsc(parcelId).stream()
                .map(ParcelMapper::toEntry)
                .toList();
    }

    private static void checkDetails(ParcelStatusCode next, String failureReason, String recipientName,
                                     String proofFileUrl) {
        boolean failure = next == ParcelStatusCode.FAILED_ATTEMPT || next == ParcelStatusCode.FAILED;
        if (failure && failureReason == null) {
            throw new BadRequestException("A failure reason is required");
        }
        if (!failure && failureReason != null) {
            throw new BadRequestException("A failure reason is only allowed for a failed delivery or attempt");
        }
        if (next == ParcelStatusCode.DELIVERED && recipientName == null && proofFileUrl == null) {
            throw new BadRequestException("Proof of delivery is required: the recipient's name or a photo");
        }
        if (next != ParcelStatusCode.DELIVERED && (recipientName != null || proofFileUrl != null)) {
            throw new BadRequestException("Proof of delivery is only allowed for a delivered parcel");
        }
    }
}

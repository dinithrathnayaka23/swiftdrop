package com.swiftdrop.delivery.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.delivery.dto.request.ReportFailureRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.FailureReasonResponse;
import com.swiftdrop.delivery.mapper.CourierDeliveryMapper;
import com.swiftdrop.delivery.model.DeliveryFailure;
import com.swiftdrop.delivery.model.FailureReason;
import com.swiftdrop.delivery.repository.DeliveryFailureRepository;
import com.swiftdrop.delivery.repository.FailureReasonRepository;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Failed deliveries from the courier app: the reasons to choose from and reporting a failure.
 *
 * <p>Reporting moves the parcel to FAILED through {@link ParcelStatusService} (which checks the
 * parcel may fail now) and records the reason in {@code delivery_failures}, in one transaction.
 * Dispatch ends the courier's assignment when it hears the status event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryFailureService {

    static final String UNKNOWN_REASON = "Choose one of the failure reasons";
    static final String NOTE_REQUIRED = "Add a note to explain this reason";

    private final DispatchApi dispatchApi;
    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;
    private final FailureReasonRepository failureReasonRepository;
    private final DeliveryFailureRepository deliveryFailureRepository;

    /** The active reasons, in display order. */
    public List<FailureReasonResponse> reasons() {
        return failureReasonRepository.findByActiveTrueOrderByDisplayOrder().stream()
                .map(reason -> new FailureReasonResponse(reason.getCode(), reason.getLabel(), reason.isNoteRequired()))
                .toList();
    }

    /**
     * Marks one of the courier's deliveries as failed.
     *
     * @return the delivery as it is after the change
     * @throws BadRequestException if the reason is unknown or inactive, or it needs a note and has none
     * @throws NotFoundException   if the parcel isn't assigned to this courier (or doesn't exist)
     * @throws ConflictException   if the parcel can't fail now (not picked up yet, or already ended)
     */
    @Transactional
    public CourierDeliveryDetailResponse reportFailure(UUID parcelId, UUID courierUserId,
                                                       ReportFailureRequest request) {
        FailureReason reason = failureReasonRepository.findByCodeAndActiveTrue(request.reasonCode().trim())
                .orElseThrow(() -> new BadRequestException(UNKNOWN_REASON));
        String note = trimToNull(request.note());
        if (reason.isNoteRequired() && note == null) {
            throw new BadRequestException(NOTE_REQUIRED);
        }
        if (!dispatchApi.isAssignedTo(parcelId, courierUserId)) {
            throw new NotFoundException(CourierDeliveryService.NOT_FOUND);
        }

        // The note goes into the reason text only, so the timeline doesn't show it twice.
        StatusHistoryEntry entry = parcelStatusService.changeStatus(parcelId,
                StatusChange.failed(courierUserId, failureText(reason, note), null));

        DeliveryFailure failure = new DeliveryFailure();
        failure.setParcelId(parcelId);
        failure.setStatusHistoryId(entry.id());
        failure.setCourierUserId(courierUserId);
        failure.setReasonCode(reason.getCode());
        failure.setNote(note);
        deliveryFailureRepository.save(failure);

        log.info("Courier {} reported parcel {} as failed ({})", courierUserId, parcelId, reason.getCode());
        return CourierDeliveryMapper.toDetail(parcelService.getParcel(parcelId),
                parcelStatusService.getHistory(parcelId));
    }

    /** "Recipient unavailable", or "Other: Gate locked" when there is a note. */
    static String failureText(FailureReason reason, String note) {
        return note == null ? reason.getLabel() : reason.getLabel() + ": " + note;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

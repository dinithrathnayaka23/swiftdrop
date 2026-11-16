package com.swiftdrop.tracking.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for the tracking screens while dispatch and the courier app (Members 6 and 7) don't
 * change statuses yet. Dev profile only; goes through the parcel module's services like any other
 * module would.
 */
@Profile("dev")
@Service
@RequiredArgsConstructor
public class DevTrackingDemoService {

    static final String FAILURE_REASON = "Demo: recipient not available";
    static final String CLOSE_REASON = "Demo: delivery closed after a failed attempt";
    static final String RECIPIENT_NAME = "Demo recipient";

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;

    /** A CREATED parcel from Colombo to Kandy, booked as {@code customerUserId}. */
    public ParcelDetails createSampleParcel(UUID customerUserId) {
        return parcelService.createParcel(new NewParcel(customerUserId, null, null,
                new NewAddress("Nimal Perera", "+94771234567", "25 Galle Road", null, "Colombo", "Colombo", "00300",
                        null),
                new NewAddress("Kamala Silva", "+94712345678", "14 Peradeniya Road", null, "Kandy", "Kandy", "20000",
                        null),
                ParcelType.PARCEL, new BigDecimal("1.8"), new BigDecimal("30"), new BigDecimal("20"),
                new BigDecimal("15"), "Books", "Call on arrival", null, new BigDecimal("650.00")));
    }

    /**
     * Moves the parcel one step along the normal flow (CREATED → … → DELIVERED; a FAILED_ATTEMPT
     * parcel is retried back to CREATED), or fails it:
     * <ul>
     *   <li>{@code target} FAILED_ATTEMPT: the courier's failed attempt (after pickup).</li>
     *   <li>{@code target} FAILED: closes a FAILED_ATTEMPT parcel; a parcel still on the way gets the
     *       attempt <em>and</em> the close (two changes, two events), so one call still ends it.</li>
     * </ul>
     *
     * @throws NotFoundException   if no parcel has this tracking number
     * @throws BadRequestException if {@code target} is anything but FAILED_ATTEMPT or FAILED
     * @throws ConflictException   if the parcel is in an end state, or can't fail yet (CREATED)
     */
    @Transactional
    public void advance(String trackingNumber, ParcelStatusCode target, UUID adminUserId) {
        ParcelDetails parcel = parcelService.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NotFoundException(PublicTrackingService.NOT_FOUND_MESSAGE));
        if (target != null && target != ParcelStatusCode.FAILED_ATTEMPT && target != ParcelStatusCode.FAILED) {
            throw new BadRequestException(
                    "Only FAILED_ATTEMPT or FAILED can be chosen; leave it out for the next normal status");
        }
        ParcelStatusCode current = parcel.currentStatus();
        if (current.isTerminal()) {
            throw new ConflictException("A parcel that is %s cannot change any more".formatted(current));
        }
        if (target == null) {
            ParcelStatusCode next = nextNormalStatus(current);
            parcelStatusService.changeStatus(parcel.id(), next == ParcelStatusCode.DELIVERED
                    ? StatusChange.delivered(adminUserId, RECIPIENT_NAME, null, null)
                    : StatusChange.to(next, adminUserId, null));
            return;
        }
        // An attempt is recorded unless we're only closing one; the parcel module refuses it (409)
        // from CREATED or from FAILED_ATTEMPT.
        if (target == ParcelStatusCode.FAILED_ATTEMPT || current != ParcelStatusCode.FAILED_ATTEMPT) {
            parcelStatusService.changeStatus(parcel.id(),
                    StatusChange.failedAttempt(adminUserId, FAILURE_REASON, null));
        }
        if (target == ParcelStatusCode.FAILED) {
            parcelStatusService.changeStatus(parcel.id(), StatusChange.failed(adminUserId, CLOSE_REASON, null));
        }
    }

    /**
     * The step that isn't a failure: the next one on the way, or CREATED (a retry) after a failed
     * attempt. Every non-end status has exactly one.
     */
    static ParcelStatusCode nextNormalStatus(ParcelStatusCode current) {
        return current.nextStatuses().stream()
                .filter(status -> status != ParcelStatusCode.FAILED_ATTEMPT && status != ParcelStatusCode.FAILED)
                .findFirst()
                .orElseThrow();
    }
}

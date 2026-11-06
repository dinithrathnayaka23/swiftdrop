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
     * Moves the parcel one step along the normal flow (CREATED → … → DELIVERED), or to FAILED when
     * {@code target} is FAILED.
     *
     * @throws NotFoundException   if no parcel has this tracking number
     * @throws BadRequestException if {@code target} is anything but FAILED
     * @throws ConflictException   if the parcel is in an end state, or can't fail yet (CREATED)
     */
    public void advance(String trackingNumber, ParcelStatusCode target, UUID adminUserId) {
        ParcelDetails parcel = parcelService.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NotFoundException(PublicTrackingService.NOT_FOUND_MESSAGE));
        if (target != null && target != ParcelStatusCode.FAILED) {
            throw new BadRequestException("Only FAILED can be chosen; leave it out for the next normal status");
        }
        ParcelStatusCode current = parcel.currentStatus();
        if (current.isTerminal()) {
            throw new ConflictException("A parcel that is %s cannot change any more".formatted(current));
        }
        ParcelStatusCode next = target != null ? target : nextNormalStatus(current);
        parcelStatusService.changeStatus(parcel.id(), switch (next) {
            case FAILED -> StatusChange.failed(adminUserId, FAILURE_REASON, null);
            case DELIVERED -> StatusChange.delivered(adminUserId, RECIPIENT_NAME, null, null);
            default -> StatusChange.to(next, adminUserId, null);
        });
    }

    /** The step that isn't FAILED; every non-end status has exactly one. */
    static ParcelStatusCode nextNormalStatus(ParcelStatusCode current) {
        return current.nextStatuses().stream()
                .filter(status -> status != ParcelStatusCode.FAILED)
                .findFirst()
                .orElseThrow();
    }
}

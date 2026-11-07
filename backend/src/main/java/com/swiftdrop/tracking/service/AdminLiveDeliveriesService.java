package com.swiftdrop.tracking.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.tracking.dto.response.LiveDeliveryResponse;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data of the admin Deliveries Tracking board: the parcels on their way, and parcels per status.
 * Read only through the parcel module's services.
 */
@Service
@RequiredArgsConstructor
public class AdminLiveDeliveriesService {

    /** Statuses on the board (frontend ACTIVE_STATUSES): picked up and not yet delivered or failed. */
    public static final Set<ParcelStatusCode> ACTIVE_STATUSES =
            EnumSet.of(ParcelStatusCode.PICKED_UP, ParcelStatusCode.IN_TRANSIT, ParcelStatusCode.OUT_FOR_DELIVERY);

    static final int MAX_PAGE_SIZE = 100;

    /** Most recently updated first. */
    private static final Comparator<ParcelDetails> NEWEST_FIRST =
            Comparator.comparing(ParcelDetails::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;
    private final ObjectProvider<CourierLookup> courierLookup;

    /**
     * Active parcels, most recently updated first.
     *
     * <p>The parcel module lists one status at a time, so the active statuses are loaded and merged
     * here. Fine for the number of parcels on their way at once; a {@code listByStatuses} in
     * ParcelService would let the database page it instead.
     *
     * @param status only this active status, or {@code null} for all of them
     * @param search part of a tracking number (case and spaces ignored), or {@code null}
     * @throws BadRequestException for a status that isn't shown on the board, or bad paging
     */
    @Transactional(readOnly = true)
    public Page<LiveDeliveryResponse> list(ParcelStatusCode status, String search, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (status != null && !ACTIVE_STATUSES.contains(status)) {
            throw new BadRequestException("status must be one of " + ACTIVE_STATUSES);
        }
        String term = search == null || search.isBlank() ? null : ParcelService.normalizeTrackingNumber(search);

        List<ParcelDetails> matches = (status == null ? ACTIVE_STATUSES : Set.of(status)).stream()
                .flatMap(code -> parcelService.listByStatus(code, Pageable.unpaged()).stream())
                .filter(parcel -> term == null || parcel.trackingNumber().contains(term))
                .sorted(NEWEST_FIRST)
                .toList();

        Pageable pageable = PageRequest.of(page, size);
        List<ParcelDetails> onPage = matches.stream()
                .skip(pageable.getOffset())
                .limit(size)
                .toList();
        Map<UUID, String> couriers = courierNames(onPage);
        List<LiveDeliveryResponse> rows = onPage.stream()
                .map(parcel -> toRow(parcel, couriers.get(parcel.id())))
                .toList();
        return new PageImpl<>(rows, pageable, matches.size());
    }

    /** Parcels per status, every status present (0 when there are none). */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> counts() {
        return parcelService.countByStatus();
    }

    private LiveDeliveryResponse toRow(ParcelDetails parcel, String courierName) {
        // One history read per row on the page (at most MAX_PAGE_SIZE): gives the version and the
        // time of the last change, which ParcelDetails doesn't carry.
        List<StatusHistoryEntry> history = parcelStatusService.getHistory(parcel.id());
        var lastChange = history.isEmpty() ? parcel.updatedAt() : history.getLast().changedAt();
        return new LiveDeliveryResponse(parcel.id(), parcel.trackingNumber(), parcel.currentStatus(),
                history.size(), parcel.pickupAddress().city(), parcel.dropoffAddress().city(), courierName,
                lastChange);
    }

    /** Courier names from the dispatch module once it provides CourierLookup; none until then. */
    private Map<UUID, String> courierNames(List<ParcelDetails> parcels) {
        CourierLookup lookup = courierLookup.getIfAvailable();
        if (lookup == null || parcels.isEmpty()) {
            return Map.of();
        }
        return lookup.currentCourierNames(parcels.stream().map(ParcelDetails::id).toList());
    }
}

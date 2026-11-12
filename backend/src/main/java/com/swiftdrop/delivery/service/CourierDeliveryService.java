package com.swiftdrop.delivery.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.delivery.dto.response.CourierDeliveryCounts;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.CourierDeliveryItemResponse;
import com.swiftdrop.delivery.dto.response.TodaysDeliveriesResponse;
import com.swiftdrop.delivery.mapper.CourierDeliveryMapper;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the courier app reads: today's deliveries, one delivery and the scanner's look-up.
 *
 * <p>A courier only sees parcels with an open assignment to them ({@link DispatchApi#isAssignedTo}).
 * Any other parcel, including one that doesn't exist, is reported as "Delivery not found" (404, not
 * 403), so a courier can't learn which parcels or tracking numbers exist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierDeliveryService {

    static final String NOT_FOUND = "Delivery not found";

    private final DispatchApi dispatchApi;
    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;

    /** The courier's open deliveries, oldest assignment first, with the counts for the cards above them. */
    public TodaysDeliveriesResponse today(UUID courierUserId) {
        List<UUID> parcelIds = dispatchApi.openParcelIds(courierUserId);
        Map<UUID, ParcelDetails> byId = parcelService.findAllByIds(parcelIds).stream()
                .collect(Collectors.toMap(ParcelDetails::id, Function.identity()));
        // findAllByIds has no order: put the parcels back in assignment order.
        List<ParcelDetails> parcels = parcelIds.stream().map(byId::get).filter(Objects::nonNull).toList();

        List<CourierDeliveryItemResponse> deliveries = parcels.stream().map(CourierDeliveryMapper::toItem).toList();
        return new TodaysDeliveriesResponse(counts(parcels), deliveries);
    }

    /** @throws NotFoundException if the parcel isn't assigned to this courier (or doesn't exist) */
    public CourierDeliveryDetailResponse get(UUID parcelId, UUID courierUserId) {
        if (!dispatchApi.isAssignedTo(parcelId, courierUserId)) {
            throw new NotFoundException(NOT_FOUND);
        }
        return detail(parcelService.getParcel(parcelId));
    }

    /**
     * The scanner's look-up. Case and all spaces in {@code trackingNumber} are ignored, so a label
     * read as "sd 2508 2400 12" still matches.
     *
     * @throws NotFoundException if no parcel has this number or it isn't assigned to this courier
     */
    public CourierDeliveryDetailResponse getByTrackingNumber(String trackingNumber, UUID courierUserId) {
        String compact = compactTrackingNumber(trackingNumber);
        if (compact.isEmpty()) {
            throw new NotFoundException(NOT_FOUND);
        }
        ParcelDetails parcel = parcelService.findByTrackingNumber(compact)
                .filter(found -> dispatchApi.isAssignedTo(found.id(), courierUserId))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return detail(parcel);
    }

    /** Removes every whitespace character; the parcel module then ignores case. */
    static String compactTrackingNumber(String trackingNumber) {
        return trackingNumber == null ? "" : trackingNumber.replaceAll("\\s+", "");
    }

    private CourierDeliveryDetailResponse detail(ParcelDetails parcel) {
        return CourierDeliveryMapper.toDetail(parcel, parcelStatusService.getHistory(parcel.id()));
    }

    /**
     * Not started = still CREATED; in progress = picked up but not yet delivered or failed. A parcel
     * that already reached an end state but whose assignment is still open counts in the total only.
     */
    private static CourierDeliveryCounts counts(List<ParcelDetails> parcels) {
        int assigned = 0;
        int inProgress = 0;
        for (ParcelDetails parcel : parcels) {
            ParcelStatusCode status = parcel.currentStatus();
            if (status == ParcelStatusCode.CREATED) {
                assigned++;
            } else if (!status.isTerminal()) {
                inProgress++;
            }
        }
        return new CourierDeliveryCounts(parcels.size(), assigned, inProgress);
    }
}

package com.swiftdrop.tracking.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.mapper.PublicTrackingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public tracking look-ups: anyone who knows a tracking number sees its public-safe status. */
@Service
@RequiredArgsConstructor
public class PublicTrackingService {

    /**
     * The same message for every unknown, blank or malformed number, so a response never tells
     * a guesser anything beyond "not found".
     */
    public static final String NOT_FOUND_MESSAGE = "No parcel was found with this tracking number";

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;

    /**
     * The parcel's public tracking view. Case and surrounding spaces in the number are ignored.
     * Parcel and history are read in one transaction so they agree with each other.
     *
     * @throws NotFoundException if no parcel has this tracking number
     */
    @Transactional(readOnly = true)
    public PublicTrackingResponse track(String trackingNumber) {
        ParcelDetails parcel = find(trackingNumber);
        return PublicTrackingMapper.toResponse(parcel, parcelStatusService.getHistory(parcel.id()));
    }

    /**
     * The stored form of a tracking number that belongs to a parcel, for example before opening its
     * live stream. Case and surrounding spaces are ignored.
     *
     * @throws NotFoundException with the same message as {@link #track}, if no parcel has this number
     */
    @Transactional(readOnly = true)
    public String requireKnownTrackingNumber(String trackingNumber) {
        return find(trackingNumber).trackingNumber();
    }

    private ParcelDetails find(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new NotFoundException(NOT_FOUND_MESSAGE);
        }
        return parcelService.findByTrackingNumber(ParcelService.normalizeTrackingNumber(trackingNumber))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
    }
}

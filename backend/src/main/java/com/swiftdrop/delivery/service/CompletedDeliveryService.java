package com.swiftdrop.delivery.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import com.swiftdrop.delivery.dto.response.CompletedDeliveryResponse;
import com.swiftdrop.delivery.repository.CompletedDeliveryQueries;
import com.swiftdrop.delivery.repository.CompletedDeliveryRow;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The courier's Completed Deliveries: their deliveries and failed attempts, newest first, from the
 * delivery module's own records. Only the courier's own rows are ever read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletedDeliveryService {

    public static final int MAX_PAGE_SIZE = 100;
    /** Without dates the list shows the last week (today and the six days before). */
    static final int DEFAULT_DAYS = 7;

    private final CompletedDeliveryQueries queries;
    private final ParcelService parcelService;
    private final CourierDay courierDay;

    /**
     * @param from    first day, inclusive, in the admin's time zone; default six days ago
     * @param to      last day, inclusive; default today
     * @param outcome {@code null} for both
     * @throws BadRequestException for a bad page or size, or {@code to} before {@code from}
     */
    public Page<CompletedDeliveryResponse> list(UUID courierUserId, LocalDate from, LocalDate to,
                                                DeliveryOutcome outcome, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        LocalDate last = to != null ? to : courierDay.today();
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1);
        if (last.isBefore(first)) {
            throw new BadRequestException("to must be on or after from");
        }

        Page<CompletedDeliveryRow> rows = queries.find(courierUserId, courierDay.startOf(first),
                courierDay.startOf(last.plusDays(1)), outcome, PageRequest.of(page, size));
        List<UUID> parcelIds = rows.getContent().stream().map(CompletedDeliveryRow::parcelId).distinct().toList();
        Map<UUID, ParcelDetails> parcels = parcelIds.isEmpty() ? Map.of()
                : parcelService.findAllByIds(parcelIds).stream()
                        .collect(Collectors.toMap(ParcelDetails::id, Function.identity()));
        return rows.map(row -> toResponse(row, parcels.get(row.parcelId())));
    }

    private static CompletedDeliveryResponse toResponse(CompletedDeliveryRow row, ParcelDetails parcel) {
        AddressDetails dropoff = parcel == null ? null : parcel.dropoffAddress();
        return new CompletedDeliveryResponse(
                row.parcelId(),
                parcel == null ? null : parcel.trackingNumber(),
                row.outcome(),
                row.completedAt(),
                dropoff == null ? null : dropoff.contactName(),
                dropoff == null ? null : dropoff.addressLine1(),
                dropoff == null ? null : dropoff.city(),
                row.receivedBy(),
                row.reasonLabel(),
                row.note(),
                row.proofId() == null ? null : DeliveryProofService.photoUrl(row.proofId()));
    }
}

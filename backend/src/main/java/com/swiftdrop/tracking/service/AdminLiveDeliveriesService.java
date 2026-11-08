package com.swiftdrop.tracking.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.tracking.dto.response.LiveDeliveryDetailResponse;
import com.swiftdrop.tracking.dto.response.LiveDeliveryDetailResponse.DeliveryAddress;
import com.swiftdrop.tracking.dto.response.LiveDeliveryDetailResponse.StatusStep;
import com.swiftdrop.tracking.dto.response.LiveDeliveryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data of the admin Deliveries Tracking screens: the table, the status counts and one delivery's
 * details. Reads only through the parcel, merchant and settings modules' services.
 */
@Service
@RequiredArgsConstructor
public class AdminLiveDeliveriesService {

    static final int MAX_PAGE_SIZE = 100;

    /** Most recently changed first. */
    private static final Comparator<ParcelDetails> NEWEST_FIRST =
            Comparator.comparing(ParcelDetails::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;
    private final MerchantService merchantService;
    private final SystemSettingsService settingsService;
    private final ObjectProvider<CourierLookup> courierLookup;

    /**
     * Filters of the table. Every field is optional.
     *
     * @param search      part of a tracking number, recipient name or merchant name (case ignored)
     * @param bookedFrom  first booking day, in the System Settings time zone
     * @param bookedTo    last booking day (whole day included)
     */
    public record Filter(ParcelStatusCode status, String search, LocalDate bookedFrom, LocalDate bookedTo) {
    }

    /**
     * Deliveries of every status, most recently changed first.
     *
     * <p>The parcel module lists one status at a time, so the statuses are loaded and filtered
     * here, in memory. Fine for this project's volume; a filtered, paged query in ParcelService
     * (Member 4) would let the database do it.
     *
     * @throws BadRequestException for bad paging or a date range that ends before it starts
     */
    @Transactional(readOnly = true)
    public Page<LiveDeliveryResponse> list(Filter filter, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (filter.bookedFrom() != null && filter.bookedTo() != null && filter.bookedTo().isBefore(filter.bookedFrom())) {
            throw new BadRequestException("bookedTo must not be before bookedFrom");
        }

        Map<UUID, String> merchants = merchantNames();
        List<ParcelStatusCode> statuses = filter.status() == null
                ? Arrays.asList(ParcelStatusCode.values())
                : List.of(filter.status());
        List<ParcelDetails> matches = statuses.stream()
                .flatMap(code -> parcelService.listByStatus(code, Pageable.unpaged()).stream())
                .filter(matchesSearch(filter.search(), merchants))
                .filter(bookedWithin(filter.bookedFrom(), filter.bookedTo()))
                .sorted(NEWEST_FIRST)
                .toList();

        Pageable pageable = PageRequest.of(page, size);
        List<ParcelDetails> onPage = matches.stream().skip(pageable.getOffset()).limit(size).toList();
        Map<UUID, String> couriers = courierNames(onPage.stream().map(ParcelDetails::id).toList());
        List<LiveDeliveryResponse> rows = onPage.stream()
                .map(parcel -> toRow(parcel, merchants.get(parcel.merchantId()), couriers.get(parcel.id())))
                .toList();
        return new PageImpl<>(rows, pageable, matches.size());
    }

    /** Parcels per status, every status present (0 when there are none). */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> counts() {
        return parcelService.countByStatus();
    }

    /**
     * One delivery, for its detail page. Case and surrounding spaces in the number are ignored.
     *
     * @throws NotFoundException if no parcel has this tracking number
     */
    @Transactional(readOnly = true)
    public LiveDeliveryDetailResponse get(String trackingNumber) {
        ParcelDetails parcel = trackingNumber == null || trackingNumber.isBlank() ? null
                : parcelService.findByTrackingNumber(trackingNumber).orElse(null);
        if (parcel == null) {
            throw new NotFoundException("No parcel was found with this tracking number");
        }
        List<StatusHistoryEntry> history = parcelStatusService.getHistory(parcel.id());
        AddressDetails dropoff = parcel.dropoffAddress();
        String merchantName = parcel.merchantId() == null ? null : merchantNames().get(parcel.merchantId());
        return new LiveDeliveryDetailResponse(parcel.id(), parcel.trackingNumber(), parcel.currentStatus(),
                history.size(), parcel.createdAt(), lastChange(parcel, history), dropoff.contactName(),
                dropoff.contactPhone(),
                new DeliveryAddress(dropoff.addressLine1(), dropoff.addressLine2(), dropoff.city(), dropoff.district(),
                        dropoff.postalCode()),
                parcel.pickupAddress().city(), merchantName, courierNames(List.of(parcel.id())).get(parcel.id()),
                parcel.parcelType(), parcel.weightKg(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm(),
                parcel.contentDescription(), parcel.deliveryInstructions(),
                history.stream().map(entry -> new StatusStep(entry.status(), entry.changedAt())).toList());
    }

    private LiveDeliveryResponse toRow(ParcelDetails parcel, String merchantName, String courierName) {
        // One history read per row on the page (at most MAX_PAGE_SIZE): gives the version and the
        // time of the last change, which ParcelDetails doesn't carry.
        List<StatusHistoryEntry> history = parcelStatusService.getHistory(parcel.id());
        AddressDetails dropoff = parcel.dropoffAddress();
        return new LiveDeliveryResponse(parcel.id(), parcel.trackingNumber(), parcel.currentStatus(),
                history.size(), dropoff.contactName(), dropoff.contactPhone(), merchantName, courierName,
                dropoff.city(), parcel.createdAt(), lastChange(parcel, history));
    }

    private static Instant lastChange(ParcelDetails parcel, List<StatusHistoryEntry> history) {
        return history.isEmpty() ? parcel.updatedAt() : history.getLast().changedAt();
    }

    private static Predicate<ParcelDetails> matchesSearch(String search, Map<UUID, String> merchants) {
        if (search == null || search.isBlank()) {
            return parcel -> true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return parcel -> contains(parcel.trackingNumber(), term)
                || contains(parcel.dropoffAddress().contactName(), term)
                || contains(merchants.get(parcel.merchantId()), term);
    }

    private static boolean contains(String value, String lowerCaseTerm) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseTerm);
    }

    /** Booking days are whole days in the admin's time zone (System Settings). */
    private Predicate<ParcelDetails> bookedWithin(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return parcel -> true;
        }
        ZoneId zone = ZoneId.of(settingsService.current().timeZone());
        Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return parcel -> (start == null || !parcel.createdAt().isBefore(start))
                && (end == null || parcel.createdAt().isBefore(end));
    }

    /** Business names of approved merchants (only they can create orders). */
    private Map<UUID, String> merchantNames() {
        return merchantService.listByStatus(MerchantApprovalStatus.APPROVED).stream()
                .collect(Collectors.toMap(MerchantResponse::merchantId, MerchantResponse::businessName,
                        (first, second) -> first));
    }

    /** Courier names from the dispatch module once it provides CourierLookup; none until then. */
    private Map<UUID, String> courierNames(List<UUID> parcelIds) {
        CourierLookup lookup = courierLookup.getIfAvailable();
        if (lookup == null || parcelIds.isEmpty()) {
            return Map.of();
        }
        return lookup.currentCourierNames(parcelIds);
    }
}

package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.MerchantOrderFilter;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.parcel.dto.MerchantParcelFilter;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The merchant's orders in the portal, read through the parcel module's public services. Callers
 * pass the ID returned by {@link MerchantAccessService#requireApprovedMerchant}.
 */
@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SEARCH_LENGTH = 100;

    /** Days in the date filters are Sri Lankan days. */
    static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;

    /** Number of the merchant's orders per status (every status, 0 when none), for the dashboard. */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> counts(UUID merchantId) {
        return parcelService.countByStatusForMerchant(merchantId);
    }

    /**
     * The merchant's orders that match every filter in {@code filter}, newest first.
     *
     * @throws BadRequestException if a filter or the paging is invalid; the message names the parameter
     */
    @Transactional(readOnly = true)
    public Page<MerchantOrderSummaryResponse> list(UUID merchantId, MerchantOrderFilter filter, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String search = StringUtils.hasText(filter.search()) ? filter.search().trim() : null;
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("search must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        ParcelStatusCode status = parseStatus(filter.status());
        LocalDate from = parseDay("from", filter.from());
        LocalDate to = parseDay("to", filter.to());
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must be on or before to");
        }
        MerchantParcelFilter parcelFilter = new MerchantParcelFilter(search, status,
                from == null ? null : from.atStartOfDay(SRI_LANKA).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(SRI_LANKA).toInstant());
        return parcelService.listByMerchant(merchantId, parcelFilter, PageRequest.of(page, size))
                .map(MerchantOrderMapper::toSummary);
    }

    /**
     * One of the merchant's orders with its timeline. Another merchant's or a customer's parcel is
     * reported as not found, so tracking numbers can't be used to probe other orders.
     */
    @Transactional(readOnly = true)
    public MerchantOrderDetailResponse get(UUID merchantId, String trackingNumber) {
        ParcelDetails parcel = parcelService.findByTrackingNumber(trackingNumber)
                .filter(found -> merchantId.equals(found.merchantId()))
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return MerchantOrderMapper.toDetail(parcel, parcelStatusService.getHistory(parcel.id()));
    }

    private static ParcelStatusCode parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ParcelStatusCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be one of " + Arrays.stream(ParcelStatusCode.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    private static LocalDate parseDay(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(name + " must be a date in the format yyyy-MM-dd");
        }
    }
}

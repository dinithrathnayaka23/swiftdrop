package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merchant's orders in the portal, read through the parcel module's public services. Callers
 * pass the ID returned by {@link MerchantAccessService#requireApprovedMerchant}.
 */
@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    static final int MAX_PAGE_SIZE = 100;

    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;

    /** Number of the merchant's orders per status (every status, 0 when none), for the dashboard. */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> counts(UUID merchantId) {
        return parcelService.countByStatusForMerchant(merchantId);
    }

    /** The merchant's orders, newest first. */
    @Transactional(readOnly = true)
    public Page<MerchantOrderSummaryResponse> list(UUID merchantId, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return parcelService.listByMerchant(merchantId, PageRequest.of(page, size))
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
}

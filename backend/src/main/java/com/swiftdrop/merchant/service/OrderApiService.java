package com.swiftdrop.merchant.service;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.service.ZoneResolver;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Merchant order API use cases, backed by the parcel and pricing module APIs. */
@Service
@RequiredArgsConstructor
public class OrderApiService {

    private final FeeApi feeApi;
    private final ZoneResolver zoneResolver;
    private final ParcelService parcelService;

    /**
     * Creates an order, or returns the existing order for a repeated merchant reference.
     *
     * <p>The fee is always calculated by the pricing module and the parcel is created through the
     * parcel module, so this service does not duplicate either module's business rules.
     */
    public OrderResponse createOrder(UUID merchantId, CreateOrderRequest request) {
        var existing = parcelService.findByMerchantOrderReference(merchantId, request.externalOrderReference());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        FeeLocation pickupLocation = toFeeLocation(request.pickupAddress());
        FeeLocation dropoffLocation = toFeeLocation(request.dropoffAddress());
        FeeEstimate estimate = feeApi.estimateFee(new FeeRequest(
                pickupLocation,
                dropoffLocation,
                request.weightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm()));

        NewParcel newParcel = new NewParcel(
                null,
                merchantId,
                request.externalOrderReference(),
                toNewAddress(request.pickupAddress(), pickupLocation),
                toNewAddress(request.dropoffAddress(), dropoffLocation),
                request.parcelType(),
                request.weightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                blankToNull(request.description()),
                null,
                estimate.pricingRuleId(),
                estimate.totalFee());

        try {
            return toResponse(parcelService.createParcel(newParcel));
        } catch (ConflictException ex) {
            // A concurrent request may have created the same reference after the initial lookup.
            return parcelService.findByMerchantOrderReference(merchantId, request.externalOrderReference())
                    .map(OrderApiService::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    /** Returns one of this merchant's orders by its SwiftDrop tracking number. */
    public OrderResponse getOrder(UUID merchantId, String trackingNumber) {
        return toOwnedParcel(merchantId, trackingNumber).map(OrderApiService::toResponse)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    /** Returns the current status of one of this merchant's orders. */
    public ParcelStatusCode getStatus(UUID merchantId, String trackingNumber) {
        return toOwnedParcel(merchantId, trackingNumber).map(ParcelDetails::currentStatus)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    private Optional<ParcelDetails> toOwnedParcel(UUID merchantId, String trackingNumber) {
        return parcelService.findByTrackingNumber(trackingNumber)
                .filter(parcel -> merchantId.equals(parcel.merchantId()));
    }

    private NewAddress toNewAddress(BookingAddressRequest address, FeeLocation location) {
        return new NewAddress(
                address.contactName().trim(),
                address.contactPhone().trim(),
                address.addressLine1().trim(),
                blankToNull(address.addressLine2()),
                address.city().trim(),
                address.district().trim(),
                blankToNull(address.postalCode()),
                zoneResolver.findZoneId(location).orElse(null));
    }

    private static FeeLocation toFeeLocation(BookingAddressRequest address) {
        return new FeeLocation(
                address.district().trim(),
                address.city().trim(),
                blankToNull(address.postalCode()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static OrderResponse toResponse(ParcelDetails parcel) {
        return new OrderResponse(
                parcel.trackingNumber(),
                parcel.externalOrderReference(),
                parcel.currentStatus(),
                parcel.quotedFee(),
                parcel.feeCurrency(),
                parcel.createdAt());
    }
}

package com.swiftdrop.booking.service;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.booking.dto.request.BookingRequest;
import com.swiftdrop.booking.dto.response.BookingConfirmationResponse;
import com.swiftdrop.booking.dto.response.FeeEstimateResponse;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.config.api.ZoneApi;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Customer delivery booking: fee estimate and booking confirmation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final FeeApi feeApi;
    private final ParcelService parcelService;
    private final SystemSettingsService settings;
    private final ZoneApi zoneApi;

    /** Shows the fee for a booking without saving anything. */
    public FeeEstimateResponse estimate(BookingRequest request) {
        checkLimits(request);
        FeeEstimate estimate = feeApi.estimateFee(toFeeRequest(request));
        return new FeeEstimateResponse(estimate.totalFee(), estimate.currency(), estimate.lines());
    }

    /** Saves the parcel for the customer. The fee is calculated here again, never taken from the browser. */
    public BookingConfirmationResponse book(BookingRequest request, UUID customerUserId) {
        checkLimits(request);
        FeeEstimate estimate = feeApi.estimateFee(toFeeRequest(request));

        NewParcel newParcel = new NewParcel(
                customerUserId,
                null,
                null,
                toAddress(request.pickup()),
                toAddress(request.dropoff()),
                request.parcelType(),
                request.weightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                blankToNull(request.contentDescription()),
                blankToNull(request.deliveryInstructions()),
                estimate.pricingRuleId(),
                estimate.totalFee());

        ParcelDetails parcel = parcelService.createParcel(newParcel);
        log.info("Parcel {} booked by customer {}", parcel.id(), customerUserId);

        return new BookingConfirmationResponse(
                parcel.id(),
                parcel.trackingNumber(),
                parcel.currentStatus(),
                parcel.quotedFee(),
                parcel.feeCurrency(),
                parcel.createdAt());
    }

    /** Refuses parcels heavier or bigger than the maximums in System Settings. */
    private void checkLimits(BookingRequest request) {
        SystemSettingsView limits = settings.current();
        if (request.weightKg().compareTo(limits.maxParcelWeightKg()) > 0) {
            throw new BadRequestException("The parcel is heavier than the maximum of "
                    + limits.maxParcelWeightKg().stripTrailingZeros().toPlainString() + " kg");
        }
        BigDecimal maxSide = limits.maxParcelDimensionCm();
        if (exceeds(request.lengthCm(), maxSide)
                || exceeds(request.widthCm(), maxSide)
                || exceeds(request.heightCm(), maxSide)) {
            throw new BadRequestException("Each side of the parcel can be at most "
                    + maxSide.stripTrailingZeros().toPlainString() + " cm");
        }
    }

    private static boolean exceeds(BigDecimal side, BigDecimal max) {
        return side != null && side.compareTo(max) > 0;
    }

    private static FeeRequest toFeeRequest(BookingRequest request) {
        return new FeeRequest(
                toFeeLocation(request.pickup()),
                toFeeLocation(request.dropoff()),
                request.weightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm());
    }

    private static FeeLocation toFeeLocation(BookingAddressRequest address) {
        return new FeeLocation(
                address.district().trim(), address.city().trim(), blankToNull(address.postalCode()));
    }

    /** Maps an address and looks up the zone that covers it (null when no active zone matches). */
    private NewAddress toAddress(BookingAddressRequest address) {
        String district = address.district().trim();
        String city = address.city().trim();
        String postalCode = blankToNull(address.postalCode());
        UUID zoneId = zoneApi.findZoneId(district, city, postalCode).orElse(null);
        return new NewAddress(
                address.contactName().trim(),
                address.contactPhone().trim(),
                address.addressLine1().trim(),
                blankToNull(address.addressLine2()),
                city,
                district,
                postalCode,
                zoneId);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
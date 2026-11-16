package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.service.ZoneResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApiServiceTest {

    private final UUID merchantId = UUID.randomUUID();
    private final UUID pickupZoneId = UUID.randomUUID();
    private final UUID dropoffZoneId = UUID.randomUUID();

    @Mock
    private FeeApi feeApi;

    @Mock
    private ZoneResolver zoneResolver;

    @Mock
    private ParcelService parcelService;

    private OrderApiService service;

    @BeforeEach
    void setUp() {
        service = new OrderApiService(feeApi, zoneResolver, parcelService);
    }

    private BookingAddressRequest address(String city, String district) {
        return new BookingAddressRequest(
                "  BrightMart ", " 0771234567 ", " 12 Main Street ", "  ",
                city, district, "");
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(
                " ORD-1001 ", address("Colombo", "Colombo"), address("Kandy", "Kandy"),
                ParcelType.PARCEL, new BigDecimal("2.500"), null, null, null, "  Books  ");
    }

    private ParcelDetails parcel(UUID owner, String trackingNumber) {
        return new ParcelDetails(
                UUID.randomUUID(), trackingNumber, null, owner, "ORD-1001", null, null,
                ParcelType.PARCEL, new BigDecimal("2.500"), null, null, null, "Books", null,
                ParcelStatusCode.CREATED, UUID.randomUUID(), new BigDecimal("650.00"), "LKR",
                Instant.parse("2026-11-12T10:00:00Z"), Instant.parse("2026-11-12T10:00:00Z"));
    }

    @Test
    void createsMerchantParcelUsingPricingAndResolvedZones() {
        FeeEstimate estimate = new FeeEstimate(
                new BigDecimal("650.00"), "LKR", UUID.randomUUID(), List.of());
        when(parcelService.findByMerchantOrderReference(merchantId, " ORD-1001 ")).thenReturn(Optional.empty());
        when(zoneResolver.findZoneId(any())).thenReturn(Optional.of(pickupZoneId), Optional.of(dropoffZoneId));
        when(feeApi.estimateFee(any(FeeRequest.class))).thenReturn(estimate);
        when(parcelService.createParcel(any(NewParcel.class))).thenReturn(parcel(merchantId, "SD7KQ2MX9TRB4H"));

        OrderResponse response = service.createOrder(merchantId, request());

        ArgumentCaptor<NewParcel> captor = ArgumentCaptor.forClass(NewParcel.class);
        verify(parcelService).createParcel(captor.capture());
        NewParcel saved = captor.getValue();
        assertThat(saved.merchantId()).isEqualTo(merchantId);
        assertThat(saved.customerUserId()).isNull();
        assertThat(saved.pickupAddress().zoneId()).isEqualTo(pickupZoneId);
        assertThat(saved.dropoffAddress().zoneId()).isEqualTo(dropoffZoneId);
        assertThat(saved.contentDescription()).isEqualTo("Books");
        assertThat(response.trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
        assertThat(response.quotedFee()).isEqualByComparingTo("650.00");
    }

    @Test
    void returnsExistingOrderWithoutRepricingOrCreating() {
        ParcelDetails existing = parcel(merchantId, "SD7KQ2MX9TRB4H");
        when(parcelService.findByMerchantOrderReference(merchantId, " ORD-1001 ")).thenReturn(Optional.of(existing));

        OrderResponse response = service.createOrder(merchantId, request());

        assertThat(response.trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
        verifyNoInteractions(feeApi, zoneResolver);
    }

    @Test
    void hidesAnotherMerchantsOrder() {
        when(parcelService.findByTrackingNumber("SD7KQ2MX9TRB4H"))
                .thenReturn(Optional.of(parcel(UUID.randomUUID(), "SD7KQ2MX9TRB4H")));

        assertThatThrownBy(() -> service.getOrder(merchantId, "SD7KQ2MX9TRB4H"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order not found");
        assertThatThrownBy(() -> service.getStatus(merchantId, "SD7KQ2MX9TRB4H"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsStatusForTheMerchantOrder() {
        when(parcelService.findByTrackingNumber("SD7KQ2MX9TRB4H"))
                .thenReturn(Optional.of(parcel(merchantId, "SD7KQ2MX9TRB4H")));

        assertThat(service.getStatus(merchantId, "SD7KQ2MX9TRB4H"))
                .isEqualTo(ParcelStatusCode.CREATED);
    }
}

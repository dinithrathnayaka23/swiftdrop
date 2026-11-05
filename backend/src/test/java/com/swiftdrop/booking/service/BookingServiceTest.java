package com.swiftdrop.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.booking.dto.request.BookingRequest;
import com.swiftdrop.booking.dto.response.BookingConfirmationResponse;
import com.swiftdrop.booking.dto.response.FeeEstimateResponse;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();

    @Mock
    private FeeApi feeApi;

    @Mock
    private ParcelService parcelService;

    @Mock
    private SystemSettingsService settings;

    @InjectMocks
    private BookingService service;

    @BeforeEach
    void setUp() {
        // limits: 30 kg and 100 cm per side. Fields that do not matter here are left null.
        when(settings.current()).thenReturn(new SystemSettingsView(
                "SwiftDrop", "info@swiftdrop.lk", null, null, "Asia/Colombo", null, null, 10, "PARCEL",
                new BigDecimal("30"), new BigDecimal("100"), null,
                false, false, false, false, false, false, 1L, Instant.now()));
    }

    private BookingAddressRequest address(String city, String district) {
        return new BookingAddressRequest(
                "  Kamala Silva ", "0771234567", "12 Main Street", "  ", city, district, "");
    }

    private BookingRequest request(String weightKg, String lengthCm) {
        return new BookingRequest(
                address("Colombo 03", "Colombo"), address("Kandy", "Kandy"), ParcelType.PARCEL,
                new BigDecimal(weightKg), lengthCm == null ? null : new BigDecimal(lengthCm),
                null, null, "Books", "  ");
    }

    private FeeEstimate estimate() {
        return new FeeEstimate(
                new BigDecimal("650.00"), "LKR", ruleId,
                List.of(new FeeLine("Base fee", new BigDecimal("350.00")),
                        new FeeLine("Extra weight (3 kg)", new BigDecimal("300.00"))));
    }

    @Test
    void estimatePassesLocationsAndWeightToTheFeeApi() {
        when(feeApi.estimateFee(any(FeeRequest.class))).thenReturn(estimate());

        FeeEstimateResponse response = service.estimate(request("3.2", null));

        ArgumentCaptor<FeeRequest> captor = ArgumentCaptor.forClass(FeeRequest.class);
        verify(feeApi).estimateFee(captor.capture());
        assertThat(captor.getValue().pickup().city()).isEqualTo("Colombo 03");
        assertThat(captor.getValue().dropoff().district()).isEqualTo("Kandy");
        assertThat(captor.getValue().pickup().postalCode()).isNull();
        assertThat(captor.getValue().weightKg()).isEqualByComparingTo("3.2");
        assertThat(response.totalFee()).isEqualByComparingTo("650.00");
        assertThat(response.lines()).hasSize(2);
    }

    @Test
    void estimateRefusesAParcelHeavierThanTheLimit() {
        assertThatThrownBy(() -> service.estimate(request("30.5", null)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(feeApi);
    }

    @Test
    void estimateRefusesASideLongerThanTheLimit() {
        assertThatThrownBy(() -> service.estimate(request("2", "100.5")))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(feeApi);
    }

    @Test
    void bookSavesTheParcelForTheCustomerWithTheServerFee() {
        when(feeApi.estimateFee(any(FeeRequest.class))).thenReturn(estimate());
        when(parcelService.createParcel(any(NewParcel.class))).thenReturn(new ParcelDetails(
                parcelId, "SD7KQ2MX9TRB4H", customerId, null, null, null, null, ParcelType.PARCEL,
                new BigDecimal("3.2"), null, null, null, "Books", null, ParcelStatusCode.CREATED,
                ruleId, new BigDecimal("650.00"), "LKR", Instant.parse("2026-11-05T05:00:00Z"), Instant.now()));

        BookingConfirmationResponse response = service.book(request("3.2", null), customerId);

        ArgumentCaptor<NewParcel> captor = ArgumentCaptor.forClass(NewParcel.class);
        verify(parcelService).createParcel(captor.capture());
        NewParcel saved = captor.getValue();
        assertThat(saved.customerUserId()).isEqualTo(customerId);
        assertThat(saved.merchantId()).isNull();
        assertThat(saved.quotedFee()).isEqualByComparingTo("650.00");
        assertThat(saved.pricingRuleId()).isEqualTo(ruleId);
        assertThat(saved.pickupAddress().contactName()).isEqualTo("Kamala Silva");
        assertThat(saved.pickupAddress().addressLine2()).isNull();
        assertThat(saved.deliveryInstructions()).isNull();
        assertThat(response.trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
        assertThat(response.totalFee()).isEqualByComparingTo("650.00");
        assertThat(response.status()).isEqualTo(ParcelStatusCode.CREATED);
    }

    @Test
    void bookDoesNotSaveAnythingWhenTheParcelIsTooHeavy() {
        assertThatThrownBy(() -> service.book(request("31", null), customerId))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(parcelService);
    }
}
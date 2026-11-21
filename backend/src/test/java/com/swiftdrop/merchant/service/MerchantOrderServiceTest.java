package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.MerchantOrderFilter;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.MerchantParcelFilter;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;
    @InjectMocks private MerchantOrderService orderService;

    private static final MerchantParcelFilter NO_FILTER = new MerchantParcelFilter(null, null, null, null);

    private final UUID merchantId = UUID.randomUUID();

    private static ParcelDetails parcel(UUID merchantId, UUID customerUserId) {
        AddressDetails pickup = new AddressDetails(UUID.randomUUID(), "BrightMart Warehouse", "+94711111111",
                "1 Depot Road", null, "Colombo", "Colombo", "00300", null);
        AddressDetails dropoff = new AddressDetails(UUID.randomUUID(), "Nimal Perera", "+94772222222",
                "25 Temple Street", "Apt 4", "Kandy", "Kandy", "20000", null);
        return new ParcelDetails(UUID.randomUUID(), "SD-20261112-ABC123", customerUserId, merchantId, "ORD-1001",
                pickup, dropoff, ParcelType.PARCEL, new BigDecimal("1.50"), null, null, null, "Shoes", "Call first",
                ParcelStatusCode.DELIVERED, UUID.randomUUID(), new BigDecimal("450.00"), "LKR",
                Instant.parse("2026-11-10T08:00:00Z"), Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void countsComeFromTheMerchantsOwnOrders() {
        orderService.counts(merchantId);

        verify(parcelService).countByStatusForMerchant(merchantId);
    }

    @Test
    void listShowsOneRowPerOrder() {
        ParcelDetails order = parcel(merchantId, null);
        Page<ParcelDetails> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(parcelService.listByMerchant(merchantId, NO_FILTER, PageRequest.of(0, 10))).thenReturn(page);

        Page<MerchantOrderSummaryResponse> result = orderService.list(merchantId, MerchantOrderFilter.none(), 0, 10);

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.trackingNumber()).isEqualTo("SD-20261112-ABC123");
            assertThat(row.externalOrderReference()).isEqualTo("ORD-1001");
            assertThat(row.recipientName()).isEqualTo("Nimal Perera");
            assertThat(row.deliveryCity()).isEqualTo("Kandy");
            assertThat(row.status()).isEqualTo(ParcelStatusCode.DELIVERED);
        });
    }

    /** Lists with {@code filter} and returns what was sent to the parcel module. */
    private MerchantParcelFilter listWith(MerchantOrderFilter filter) {
        when(parcelService.listByMerchant(eq(merchantId), any(), eq(PageRequest.of(0, 10))))
                .thenReturn(Page.empty());
        orderService.list(merchantId, filter, 0, 10);
        ArgumentCaptor<MerchantParcelFilter> sent = ArgumentCaptor.forClass(MerchantParcelFilter.class);
        verify(parcelService).listByMerchant(eq(merchantId), sent.capture(), eq(PageRequest.of(0, 10)));
        return sent.getValue();
    }

    @Test
    void searchIsTrimmed() {
        assertThat(listWith(new MerchantOrderFilter("  ORD-1001 ", null, null, null)).search())
                .isEqualTo("ORD-1001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void missingFiltersFilterNothing(String blank) {
        assertThat(listWith(new MerchantOrderFilter(blank, blank, blank, blank))).isEqualTo(NO_FILTER);
    }

    @Test
    void statusIsReadIgnoringCase() {
        assertThat(listWith(new MerchantOrderFilter(null, " in_transit ", null, null)).status())
                .isEqualTo(ParcelStatusCode.IN_TRANSIT);
    }

    @Test
    void datesCoverWholeSriLankanDays() {
        MerchantParcelFilter sent = listWith(new MerchantOrderFilter(null, null, "2026-11-01", "2026-11-03"));

        // Midnight in Colombo (UTC+05:30) at the start of the 1st, up to midnight at the end of the 3rd.
        assertThat(sent.createdFrom()).isEqualTo(Instant.parse("2026-10-31T18:30:00Z"));
        assertThat(sent.createdTo()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
    }

    @Test
    void oneDayRangeIsAllowed() {
        MerchantParcelFilter sent = listWith(new MerchantOrderFilter(null, null, "2026-11-02", "2026-11-02"));

        assertThat(Duration.between(sent.createdFrom(), sent.createdTo())).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void rangeFromADayHasNoEnd() {
        MerchantParcelFilter sent = listWith(new MerchantOrderFilter(null, null, "2026-11-02", null));

        assertThat(sent.createdFrom()).isNotNull();
        assertThat(sent.createdTo()).isNull();
    }

    @Test
    void rangeUpToADayHasNoStart() {
        MerchantParcelFilter sent = listWith(new MerchantOrderFilter(null, null, null, "2026-11-02"));

        assertThat(sent.createdFrom()).isNull();
        assertThat(sent.createdTo()).isNotNull();
    }

    @Test
    void filtersCombine() {
        MerchantParcelFilter sent = listWith(
                new MerchantOrderFilter("kandy", "DELIVERED", "2026-11-01", "2026-11-30"));

        assertThat(sent.search()).isEqualTo("kandy");
        assertThat(sent.status()).isEqualTo(ParcelStatusCode.DELIVERED);
        assertThat(sent.createdFrom()).isEqualTo(Instant.parse("2026-10-31T18:30:00Z"));
        assertThat(sent.createdTo()).isEqualTo(Instant.parse("2026-11-30T18:30:00Z"));
    }

    @Test
    void searchAtTheLengthLimitIsAllowed() {
        String longest = "x".repeat(MerchantOrderService.MAX_SEARCH_LENGTH);

        assertThat(listWith(new MerchantOrderFilter(longest, null, null, null)).search()).isEqualTo(longest);
    }

    @Test
    void invalidPagingIsRejected() {
        MerchantOrderFilter none = MerchantOrderFilter.none();
        assertThatThrownBy(() -> orderService.list(merchantId, none, -1, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> orderService.list(merchantId, none, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> orderService.list(merchantId, none, 0, MerchantOrderService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcelService);
    }

    static Stream<Arguments> badFilters() {
        return Stream.of(
                Arguments.of(new MerchantOrderFilter(null, "LOST", null, null),
                        "status must be one of CREATED, PICKED_UP, "),
                Arguments.of(new MerchantOrderFilter(null, null, "2026-13-01", null),
                        "from must be a date in the format yyyy-MM-dd"),
                Arguments.of(new MerchantOrderFilter(null, null, "01/11/2026", null),
                        "from must be a date in the format yyyy-MM-dd"),
                Arguments.of(new MerchantOrderFilter(null, null, null, "yesterday"),
                        "to must be a date in the format yyyy-MM-dd"),
                Arguments.of(new MerchantOrderFilter(null, null, "2026-11-03", "2026-11-02"),
                        "from must be on or before to"),
                Arguments.of(new MerchantOrderFilter("x".repeat(MerchantOrderService.MAX_SEARCH_LENGTH + 1),
                        null, null, null), "search must be at most 100 characters"));
    }

    @ParameterizedTest
    @MethodSource("badFilters")
    void badFiltersAreRejectedWithAMessageNamingTheParameter(MerchantOrderFilter filter, String message) {
        assertThatThrownBy(() -> orderService.list(merchantId, filter, 0, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith(message);
        verifyNoInteractions(parcelService);
    }

    @Test
    void detailIncludesTheTimelineWithoutWhoChangedItOrTheProofFile() {
        ParcelDetails order = parcel(merchantId, null);
        UUID courierUserId = UUID.randomUUID();
        StatusHistoryEntry created = new StatusHistoryEntry(UUID.randomUUID(), order.id(), null,
                ParcelStatusCode.CREATED, null, null, null, null, null, Instant.parse("2026-11-10T08:00:00Z"));
        StatusHistoryEntry delivered = new StatusHistoryEntry(UUID.randomUUID(), order.id(),
                ParcelStatusCode.OUT_FOR_DELIVERY, ParcelStatusCode.DELIVERED, courierUserId, null, null,
                "Nimal Perera", "https://files.example/proof.jpg", Instant.parse("2026-11-11T15:00:00Z"));
        when(parcelService.findByTrackingNumber("sd-20261112-abc123")).thenReturn(Optional.of(order));
        when(parcelStatusService.getHistory(order.id())).thenReturn(List.of(created, delivered));

        MerchantOrderDetailResponse detail = orderService.get(merchantId, "sd-20261112-abc123");

        assertThat(detail.dropoffAddress().addressLine2()).isEqualTo("Apt 4");
        assertThat(detail.deliveryInstructions()).isEqualTo("Call first");
        assertThat(detail.timeline()).extracting(MerchantOrderDetailResponse.TimelineEntry::status)
                .containsExactly(ParcelStatusCode.CREATED, ParcelStatusCode.DELIVERED);
        assertThat(detail.timeline().get(1).recipientName()).isEqualTo("Nimal Perera");
        assertThat(detail.toString()).doesNotContain(courierUserId.toString(), "proof.jpg");
    }

    @Test
    void anotherMerchantsOrderIsNotFound() {
        when(parcelService.findByTrackingNumber(any())).thenReturn(Optional.of(parcel(UUID.randomUUID(), null)));

        assertThatThrownBy(() -> orderService.get(merchantId, "SD-20261112-ABC123"))
                .isInstanceOf(NotFoundException.class);
        verify(parcelStatusService, never()).getHistory(any());
    }

    @Test
    void customerBookingIsNotFound() {
        when(parcelService.findByTrackingNumber(any())).thenReturn(Optional.of(parcel(null, UUID.randomUUID())));

        assertThatThrownBy(() -> orderService.get(merchantId, "SD-20261112-ABC123"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownTrackingNumberIsNotFound() {
        when(parcelService.findByTrackingNumber(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(merchantId, "SD-NOPE"))
                .isInstanceOf(NotFoundException.class);
    }
}

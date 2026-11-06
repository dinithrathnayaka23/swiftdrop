package com.swiftdrop.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.tracking.dto.response.PublicTrackingEventResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicTrackingServiceTest {

    private static final String NUMBER = "SD12345678";
    private static final Instant BOOKED = Instant.parse("2026-11-01T04:00:00Z");
    private static final Instant PICKED_UP = Instant.parse("2026-11-01T06:30:00Z");

    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;

    private PublicTrackingService service;
    private final UUID parcelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PublicTrackingService(parcelService, parcelStatusService);
    }

    private static AddressDetails address(String city) {
        return new AddressDetails(UUID.randomUUID(), "Ayesha Khan", "+94771234567", "12 Galle Road", null, city,
                "Western", null, null);
    }

    private ParcelDetails parcel() {
        return new ParcelDetails(parcelId, NUMBER, UUID.randomUUID(), null, null, address("Colombo"),
                address("Kandy"), ParcelType.PARCEL, new BigDecimal("2.5"), null, null, null, null, null,
                ParcelStatusCode.PICKED_UP, null, new BigDecimal("750.00"), "LKR", BOOKED, PICKED_UP);
    }

    private List<StatusHistoryEntry> history() {
        return List.of(
                new StatusHistoryEntry(UUID.randomUUID(), parcelId, null, ParcelStatusCode.CREATED, null, null, null,
                        null, null, BOOKED),
                new StatusHistoryEntry(UUID.randomUUID(), parcelId, ParcelStatusCode.CREATED,
                        ParcelStatusCode.PICKED_UP, null, "Collected", null, null, null, PICKED_UP));
    }

    @Test
    void aKnownNumberReturnsItsPublicTracking() {
        when(parcelService.findByTrackingNumber(NUMBER)).thenReturn(Optional.of(parcel()));
        when(parcelStatusService.getHistory(parcelId)).thenReturn(history());

        PublicTrackingResponse response = service.track(NUMBER);

        assertThat(response.trackingNumber()).isEqualTo(NUMBER);
        assertThat(response.currentStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
        assertThat(response.fromCity()).isEqualTo("Colombo");
        assertThat(response.toCity()).isEqualTo("Kandy");
        assertThat(response.lastUpdatedAt()).isEqualTo(PICKED_UP);
        assertThat(response.history()).containsExactly(
                new PublicTrackingEventResponse(ParcelStatusCode.CREATED, BOOKED),
                new PublicTrackingEventResponse(ParcelStatusCode.PICKED_UP, PICKED_UP));
    }

    @Test
    void lowerCaseAndSurroundingSpacesAreIgnored() {
        when(parcelService.findByTrackingNumber(NUMBER)).thenReturn(Optional.of(parcel()));
        when(parcelStatusService.getHistory(parcelId)).thenReturn(history());

        PublicTrackingResponse response = service.track("  sd12345678 \t");

        assertThat(response.trackingNumber()).isEqualTo(NUMBER);
        verify(parcelService).findByTrackingNumber(NUMBER);
    }

    @Test
    void anUnknownNumberIsNotFoundWithTheGenericMessage() {
        when(parcelService.findByTrackingNumber(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.track("SD00000000"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(PublicTrackingService.NOT_FOUND_MESSAGE);
        assertThatThrownBy(() -> service.track("anything else"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(PublicTrackingService.NOT_FOUND_MESSAGE);
        verify(parcelStatusService, never()).getHistory(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    void aBlankNumberIsNotFoundWithoutALookUp(String trackingNumber) {
        assertThatThrownBy(() -> service.track(trackingNumber))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(PublicTrackingService.NOT_FOUND_MESSAGE);
        verifyNoInteractions(parcelService, parcelStatusService);
    }
}

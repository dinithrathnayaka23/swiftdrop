package com.swiftdrop.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import com.swiftdrop.delivery.dto.response.CompletedDeliveryResponse;
import com.swiftdrop.delivery.repository.CompletedDeliveryQueries;
import com.swiftdrop.delivery.repository.CompletedDeliveryRow;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompletedDeliveryServiceTest {

    private static final UUID COURIER = UUID.randomUUID();
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock private CompletedDeliveryQueries queries;
    @Mock private ParcelService parcelService;
    @Mock private CourierDay courierDay;
    @InjectMocks private CompletedDeliveryService service;

    @BeforeEach
    void colomboDays() {
        when(courierDay.today()).thenReturn(TODAY);
        when(courierDay.startOf(any())).thenAnswer(call ->
                call.<LocalDate>getArgument(0).atStartOfDay(COLOMBO).toInstant());
    }

    private static ParcelDetails parcel(UUID id, String trackingNumber) {
        AddressDetails dropoff = new AddressDetails(UUID.randomUUID(), "Kasun Perera", "+94771234567",
                "12 Galle Road", null, "Colombo 03", "Colombo", null, null);
        return new ParcelDetails(id, trackingNumber, UUID.randomUUID(), null, null, dropoff, dropoff,
                ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, ParcelStatusCode.DELIVERED, null,
                BigDecimal.TEN, "LKR", Instant.now(), Instant.now());
    }

    @Test
    void mapsDeliveriesAndFailedAttemptsWithTheirParcels() {
        UUID delivered = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-15T05:00:00Z");
        when(queries.find(eq(COURIER), any(), any(), eq(null), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(
                        new CompletedDeliveryRow(delivered, DeliveryOutcome.DELIVERED, at, "Nimali", null, null,
                                proofId),
                        new CompletedDeliveryRow(failed, DeliveryOutcome.FAILED_ATTEMPT, at.minusSeconds(60), null,
                                "Recipient unavailable", "No answer", null)),
                        PageRequest.of(0, 20), 2));
        when(parcelService.findAllByIds(List.of(delivered, failed)))
                .thenReturn(List.of(parcel(failed, "SD2"), parcel(delivered, "SD1")));

        Page<CompletedDeliveryResponse> page = service.list(COURIER, null, null, null, 0, 20);

        CompletedDeliveryResponse first = page.getContent().get(0);
        assertThat(first.trackingNumber()).isEqualTo("SD1");
        assertThat(first.outcome()).isEqualTo(DeliveryOutcome.DELIVERED);
        assertThat(first.receivedBy()).isEqualTo("Nimali");
        assertThat(first.recipientName()).isEqualTo("Kasun Perera");
        assertThat(first.dropoffArea()).isEqualTo("12 Galle Road");
        assertThat(first.dropoffCity()).isEqualTo("Colombo 03");
        assertThat(first.proofPhotoUrl()).isEqualTo("/proofs/" + proofId + "/photo");
        CompletedDeliveryResponse second = page.getContent().get(1);
        assertThat(second.trackingNumber()).isEqualTo("SD2");
        assertThat(second.failureReason()).isEqualTo("Recipient unavailable");
        assertThat(second.note()).isEqualTo("No answer");
        assertThat(second.proofPhotoUrl()).isNull();
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void withoutDatesItShowsTheLastSevenDaysUpToTheEndOfToday() {
        when(queries.find(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(COURIER, null, null, DeliveryOutcome.FAILED_ATTEMPT, 0, 20);

        // 9 Nov 00:00 to 16 Nov 00:00, Colombo time.
        verify(queries).find(COURIER, Instant.parse("2026-11-08T18:30:00Z"), Instant.parse("2026-11-15T18:30:00Z"),
                DeliveryOutcome.FAILED_ATTEMPT, PageRequest.of(0, 20));
        verifyNoInteractions(parcelService);
    }

    @Test
    void bothDatesAreIncluded() {
        when(queries.find(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(COURIER, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null, 2, 5);

        verify(queries).find(COURIER, Instant.parse("2026-11-02T18:30:00Z"), Instant.parse("2026-11-03T18:30:00Z"),
                null, PageRequest.of(2, 5));
    }

    @Test
    void refusesABadRangeOrPage() {
        LocalDate later = LocalDate.of(2026, 11, 10);
        LocalDate earlier = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> service.list(COURIER, later, earlier, null, 0, 20))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("to must be on or after from");
        assertThatThrownBy(() -> service.list(COURIER, null, null, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(COURIER, null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(COURIER, null, null, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(queries);
    }
}

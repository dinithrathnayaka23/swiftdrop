package com.swiftdrop.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.CourierDeliveryItemResponse;
import com.swiftdrop.delivery.dto.response.TodaysDeliveriesResponse;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourierDeliveryServiceTest {

    private static final UUID COURIER = UUID.randomUUID();

    @Mock private DispatchApi dispatchApi;
    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;
    @InjectMocks private CourierDeliveryService service;

    private static AddressDetails address(String name, String city) {
        return new AddressDetails(UUID.randomUUID(), name, "+94771234567", "12 Galle Road", null, city, "Western",
                null, null);
    }

    private static ParcelDetails parcel(String trackingNumber, ParcelStatusCode status) {
        return new ParcelDetails(UUID.randomUUID(), trackingNumber, UUID.randomUUID(), null, null,
                address("Sender", "Kandy"), address("Recipient", "Colombo"), ParcelType.PARCEL, BigDecimal.ONE,
                null, null, null, "Books", "Leave at the gate", status, null, BigDecimal.TEN, "LKR",
                Instant.parse("2026-11-12T03:00:00Z"), Instant.parse("2026-11-12T03:00:00Z"));
    }

    @Test
    void todayKeepsTheAssignmentOrderAndCountsByStatus() {
        ParcelDetails first = parcel("SD1", ParcelStatusCode.OUT_FOR_DELIVERY);
        ParcelDetails second = parcel("SD2", ParcelStatusCode.CREATED);
        ParcelDetails third = parcel("SD3", ParcelStatusCode.PICKED_UP);
        when(dispatchApi.openParcelIds(COURIER)).thenReturn(List.of(first.id(), second.id(), third.id()));
        // The parcel module returns them in no particular order.
        when(parcelService.findAllByIds(List.of(first.id(), second.id(), third.id())))
                .thenReturn(List.of(third, first, second));

        TodaysDeliveriesResponse today = service.today(COURIER);

        assertThat(today.deliveries()).extracting(CourierDeliveryItemResponse::trackingNumber)
                .containsExactly("SD1", "SD2", "SD3");
        assertThat(today.counts().total()).isEqualTo(3);
        assertThat(today.counts().assigned()).isEqualTo(1);
        assertThat(today.counts().inProgress()).isEqualTo(2);
    }

    @Test
    void todayItemsCarryTheNextStatusesAndThePickupOnlyBeforePickup() {
        ParcelDetails waiting = parcel("SD1", ParcelStatusCode.CREATED);
        ParcelDetails out = parcel("SD2", ParcelStatusCode.OUT_FOR_DELIVERY);
        when(dispatchApi.openParcelIds(COURIER)).thenReturn(List.of(waiting.id(), out.id()));
        when(parcelService.findAllByIds(any())).thenReturn(List.of(waiting, out));

        List<CourierDeliveryItemResponse> items = service.today(COURIER).deliveries();

        assertThat(items.get(0).allowedActions()).containsExactly(ParcelStatusCode.PICKED_UP);
        assertThat(items.get(0).pickup().city()).isEqualTo("Kandy");
        assertThat(items.get(1).allowedActions())
                .containsExactly(ParcelStatusCode.DELIVERED, ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(items.get(1).pickup()).isNull();
        assertThat(items.get(1).dropoff().contactName()).isEqualTo("Recipient");
    }

    @Test
    void todayIsEmptyWithoutAssignmentsAndSkipsParcelsThatAreGone() {
        ParcelDetails kept = parcel("SD1", ParcelStatusCode.IN_TRANSIT);
        when(dispatchApi.openParcelIds(COURIER)).thenReturn(List.of(UUID.randomUUID(), kept.id()));
        when(parcelService.findAllByIds(any())).thenReturn(List.of(kept));

        assertThat(service.today(COURIER).deliveries()).hasSize(1);

        when(dispatchApi.openParcelIds(COURIER)).thenReturn(List.of());
        when(parcelService.findAllByIds(List.of())).thenReturn(List.of());
        TodaysDeliveriesResponse empty = service.today(COURIER);
        assertThat(empty.deliveries()).isEmpty();
        assertThat(empty.counts().total()).isZero();
    }

    @Test
    void anEndStateCountsInTheTotalOnlyAndHasNoActions() {
        ParcelDetails delivered = parcel("SD1", ParcelStatusCode.DELIVERED);
        when(dispatchApi.openParcelIds(COURIER)).thenReturn(List.of(delivered.id()));
        when(parcelService.findAllByIds(any())).thenReturn(List.of(delivered));

        TodaysDeliveriesResponse today = service.today(COURIER);

        assertThat(today.counts().total()).isEqualTo(1);
        assertThat(today.counts().assigned()).isZero();
        assertThat(today.counts().inProgress()).isZero();
        assertThat(today.deliveries().get(0).allowedActions()).isEmpty();
    }

    @Test
    void getReturnsTheDetailsAndTimelineOfAnAssignedParcel() {
        ParcelDetails parcel = parcel("SD1", ParcelStatusCode.PICKED_UP);
        when(dispatchApi.isAssignedTo(parcel.id(), COURIER)).thenReturn(true);
        when(parcelService.getParcel(parcel.id())).thenReturn(parcel);
        when(parcelStatusService.getHistory(parcel.id())).thenReturn(List.of(
                entry(parcel.id(), null, ParcelStatusCode.CREATED),
                entry(parcel.id(), ParcelStatusCode.CREATED, ParcelStatusCode.PICKED_UP)));

        CourierDeliveryDetailResponse detail = service.get(parcel.id(), COURIER);

        assertThat(detail.trackingNumber()).isEqualTo("SD1");
        assertThat(detail.pickup().city()).isEqualTo("Kandy");
        assertThat(detail.allowedActions())
                .containsExactly(ParcelStatusCode.IN_TRANSIT, ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(detail.timeline()).extracting(t -> t.status())
                .containsExactly(ParcelStatusCode.CREATED, ParcelStatusCode.PICKED_UP);
    }

    @Test
    void anotherCouriersParcelIsNotFoundAndNothingIsRead() {
        UUID parcelId = UUID.randomUUID();
        when(dispatchApi.isAssignedTo(parcelId, COURIER)).thenReturn(false);

        assertThatThrownBy(() -> service.get(parcelId, COURIER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(CourierDeliveryService.NOT_FOUND);
        verify(parcelService, never()).getParcel(any());
        verify(parcelStatusService, never()).getHistory(any());
    }

    @Test
    void theScannerLookUpIgnoresSpacesAndChecksOwnership() {
        ParcelDetails parcel = parcel("SD250824001", ParcelStatusCode.IN_TRANSIT);
        when(parcelService.findByTrackingNumber("sd250824001")).thenReturn(Optional.of(parcel));
        when(dispatchApi.isAssignedTo(parcel.id(), COURIER)).thenReturn(true);
        when(parcelStatusService.getHistory(parcel.id())).thenReturn(List.of());

        assertThat(service.getByTrackingNumber(" sd 2508 2400 1\n", COURIER).parcelId()).isEqualTo(parcel.id());
    }

    @Test
    void theScannerLookUpHidesUnknownAndOtherCouriersParcels() {
        ParcelDetails other = parcel("SD2", ParcelStatusCode.IN_TRANSIT);
        when(parcelService.findByTrackingNumber("SD1")).thenReturn(Optional.empty());
        when(parcelService.findByTrackingNumber("SD2")).thenReturn(Optional.of(other));
        when(dispatchApi.isAssignedTo(other.id(), COURIER)).thenReturn(false);

        assertThatThrownBy(() -> service.getByTrackingNumber("SD1", COURIER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByTrackingNumber("SD2", COURIER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByTrackingNumber("   ", COURIER)).isInstanceOf(NotFoundException.class);
        verify(parcelStatusService, never()).getHistory(any());
    }

    @Test
    void updateStatusRecordsTheStepAsTheCourierAndReturnsTheNewState() {
        ParcelDetails before = parcel("SD1", ParcelStatusCode.CREATED);
        ParcelDetails after = withStatus(before, ParcelStatusCode.PICKED_UP);
        when(dispatchApi.isAssignedTo(before.id(), COURIER)).thenReturn(true);
        when(parcelService.getParcel(before.id())).thenReturn(after);
        when(parcelStatusService.getHistory(before.id())).thenReturn(List.of(
                entry(before.id(), null, ParcelStatusCode.CREATED),
                entry(before.id(), ParcelStatusCode.CREATED, ParcelStatusCode.PICKED_UP)));

        CourierDeliveryDetailResponse detail = service.updateStatus(before.id(), COURIER,
                new UpdateDeliveryStatusRequest(ParcelStatusCode.PICKED_UP, "Collected from the front desk"));

        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(eq(before.id()), change.capture());
        assertThat(change.getValue().newStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
        assertThat(change.getValue().changedByUserId()).isEqualTo(COURIER);
        assertThat(change.getValue().note()).isEqualTo("Collected from the front desk");
        assertThat(change.getValue().failureReason()).isNull();
        assertThat(change.getValue().recipientName()).isNull();

        assertThat(detail.currentStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
        assertThat(detail.allowedActions())
                .containsExactly(ParcelStatusCode.IN_TRANSIT, ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(detail.timeline()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = {"DELIVERED", "FAILED_ATTEMPT", "FAILED", "CREATED"})
    void updateStatusRefusesStatusesWithTheirOwnScreen(ParcelStatusCode status) {
        UUID parcelId = UUID.randomUUID();

        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest(status, null);

        assertThatThrownBy(() -> service.updateStatus(parcelId, COURIER, request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(dispatchApi, parcelStatusService, parcelService);
    }

    @Test
    void updateStatusOfAnotherCouriersParcelIsNotFoundAndChangesNothing() {
        UUID parcelId = UUID.randomUUID();
        when(dispatchApi.isAssignedTo(parcelId, COURIER)).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(parcelId, COURIER,
                new UpdateDeliveryStatusRequest(ParcelStatusCode.PICKED_UP, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(CourierDeliveryService.NOT_FOUND);
        verify(parcelStatusService, never()).changeStatus(any(), any());
    }

    @Test
    void updateStatusPassesOnTheStateMachinesConflict() {
        UUID parcelId = UUID.randomUUID();
        when(dispatchApi.isAssignedTo(parcelId, COURIER)).thenReturn(true);
        when(parcelStatusService.changeStatus(eq(parcelId), any()))
                .thenThrow(new ConflictException("A parcel that is PICKED_UP cannot be changed to PICKED_UP"));

        assertThatThrownBy(() -> service.updateStatus(parcelId, COURIER,
                new UpdateDeliveryStatusRequest(ParcelStatusCode.PICKED_UP, null)))
                .isInstanceOf(ConflictException.class);
        verify(parcelService, never()).getParcel(any());
    }

    private static ParcelDetails withStatus(ParcelDetails p, ParcelStatusCode status) {
        return new ParcelDetails(p.id(), p.trackingNumber(), p.customerUserId(), p.merchantId(),
                p.externalOrderReference(), p.pickupAddress(), p.dropoffAddress(), p.parcelType(), p.weightKg(),
                p.lengthCm(), p.widthCm(), p.heightCm(), p.contentDescription(), p.deliveryInstructions(), status,
                p.pricingRuleId(), p.quotedFee(), p.feeCurrency(), p.createdAt(), p.updatedAt());
    }

    private static StatusHistoryEntry entry(UUID parcelId, ParcelStatusCode previous, ParcelStatusCode status) {
        return new StatusHistoryEntry(UUID.randomUUID(), parcelId, previous, status, COURIER, null, null, null, null,
                Instant.now());
    }
}

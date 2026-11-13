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
import com.swiftdrop.delivery.dto.request.ReportFailureRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.FailureReasonResponse;
import com.swiftdrop.delivery.model.DeliveryFailure;
import com.swiftdrop.delivery.model.FailureReason;
import com.swiftdrop.delivery.repository.DeliveryFailureRepository;
import com.swiftdrop.delivery.repository.FailureReasonRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryFailureServiceTest {

    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID PARCEL = UUID.randomUUID();

    @Mock private DispatchApi dispatchApi;
    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;
    @Mock private FailureReasonRepository failureReasonRepository;
    @Mock private DeliveryFailureRepository deliveryFailureRepository;
    @InjectMocks private DeliveryFailureService service;

    private static FailureReason reason(String code, String label, boolean noteRequired) {
        FailureReason reason = new FailureReason();
        reason.setCode(code);
        reason.setLabel(label);
        reason.setNoteRequired(noteRequired);
        return reason;
    }

    private static ParcelDetails failedParcel() {
        AddressDetails address = new AddressDetails(UUID.randomUUID(), "Recipient", "+94771234567", "12 Galle Road",
                null, "Colombo", "Western", null, null);
        return new ParcelDetails(PARCEL, "SD1", UUID.randomUUID(), null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, ParcelStatusCode.FAILED, null, BigDecimal.TEN, "LKR",
                Instant.now(), Instant.now());
    }

    private static StatusHistoryEntry failedEntry(UUID entryId) {
        return new StatusHistoryEntry(entryId, PARCEL, ParcelStatusCode.OUT_FOR_DELIVERY, ParcelStatusCode.FAILED,
                COURIER, null, "Recipient unavailable", null, null, Instant.now());
    }

    @Test
    void reasonsAreTheActiveOnesInOrder() {
        when(failureReasonRepository.findByActiveTrueOrderByDisplayOrder()).thenReturn(List.of(
                reason("RECIPIENT_UNAVAILABLE", "Recipient unavailable", false), reason("OTHER", "Other", true)));

        assertThat(service.reasons()).containsExactly(
                new FailureReasonResponse("RECIPIENT_UNAVAILABLE", "Recipient unavailable", false),
                new FailureReasonResponse("OTHER", "Other", true));
    }

    @Test
    void reportingMovesTheParcelToFailedAndRecordsTheReason() {
        UUID entryId = UUID.randomUUID();
        when(failureReasonRepository.findByCodeAndActiveTrue("OTHER")).thenReturn(
                Optional.of(reason("OTHER", "Other", true)));
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(parcelStatusService.changeStatus(eq(PARCEL), any())).thenReturn(failedEntry(entryId));
        when(parcelService.getParcel(PARCEL)).thenReturn(failedParcel());
        when(parcelStatusService.getHistory(PARCEL)).thenReturn(List.of(failedEntry(entryId)));

        CourierDeliveryDetailResponse detail = service.reportFailure(PARCEL, COURIER,
                new ReportFailureRequest(" OTHER ", "  Gate locked, no answer  "));

        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(eq(PARCEL), change.capture());
        assertThat(change.getValue().newStatus()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(change.getValue().changedByUserId()).isEqualTo(COURIER);
        assertThat(change.getValue().failureReason()).isEqualTo("Other: Gate locked, no answer");
        assertThat(change.getValue().note()).isNull();

        ArgumentCaptor<DeliveryFailure> saved = ArgumentCaptor.forClass(DeliveryFailure.class);
        verify(deliveryFailureRepository).save(saved.capture());
        assertThat(saved.getValue().getParcelId()).isEqualTo(PARCEL);
        assertThat(saved.getValue().getStatusHistoryId()).isEqualTo(entryId);
        assertThat(saved.getValue().getCourierUserId()).isEqualTo(COURIER);
        assertThat(saved.getValue().getReasonCode()).isEqualTo("OTHER");
        assertThat(saved.getValue().getNote()).isEqualTo("Gate locked, no answer");

        assertThat(detail.currentStatus()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(detail.allowedActions()).isEmpty();
    }

    @Test
    void withoutANoteTheReasonTextIsTheLabel() {
        when(failureReasonRepository.findByCodeAndActiveTrue("RECIPIENT_UNAVAILABLE")).thenReturn(
                Optional.of(reason("RECIPIENT_UNAVAILABLE", "Recipient unavailable", false)));
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(parcelStatusService.changeStatus(eq(PARCEL), any())).thenReturn(failedEntry(UUID.randomUUID()));
        when(parcelService.getParcel(PARCEL)).thenReturn(failedParcel());

        service.reportFailure(PARCEL, COURIER, new ReportFailureRequest("RECIPIENT_UNAVAILABLE", "   "));

        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(eq(PARCEL), change.capture());
        assertThat(change.getValue().failureReason()).isEqualTo("Recipient unavailable");
        ArgumentCaptor<DeliveryFailure> saved = ArgumentCaptor.forClass(DeliveryFailure.class);
        verify(deliveryFailureRepository).save(saved.capture());
        assertThat(saved.getValue().getNote()).isNull();
    }

    @Test
    void anUnknownOrInactiveReasonIsABadRequest() {
        when(failureReasonRepository.findByCodeAndActiveTrue("LOST_IN_SPACE")).thenReturn(Optional.empty());
        ReportFailureRequest request = new ReportFailureRequest("LOST_IN_SPACE", null);

        assertThatThrownBy(() -> service.reportFailure(PARCEL, COURIER, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(DeliveryFailureService.UNKNOWN_REASON);
        verifyNoInteractions(dispatchApi, parcelStatusService, deliveryFailureRepository);
    }

    @Test
    void aReasonThatNeedsANoteIsRefusedWithoutOne() {
        when(failureReasonRepository.findByCodeAndActiveTrue("DAMAGED")).thenReturn(
                Optional.of(reason("DAMAGED", "Parcel damaged", true)));
        ReportFailureRequest request = new ReportFailureRequest("DAMAGED", "  ");

        assertThatThrownBy(() -> service.reportFailure(PARCEL, COURIER, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(DeliveryFailureService.NOTE_REQUIRED);
        verifyNoInteractions(dispatchApi, parcelStatusService, deliveryFailureRepository);
    }

    @Test
    void anotherCouriersParcelIsNotFoundAndNothingChanges() {
        when(failureReasonRepository.findByCodeAndActiveTrue("REFUSED")).thenReturn(
                Optional.of(reason("REFUSED", "Refused by recipient", false)));
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(false);
        ReportFailureRequest request = new ReportFailureRequest("REFUSED", null);

        assertThatThrownBy(() -> service.reportFailure(PARCEL, COURIER, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(CourierDeliveryService.NOT_FOUND);
        verifyNoInteractions(parcelStatusService, deliveryFailureRepository);
    }

    @Test
    void aParcelThatCannotFailNowIsAConflictAndNoFailureIsSaved() {
        when(failureReasonRepository.findByCodeAndActiveTrue("REFUSED")).thenReturn(
                Optional.of(reason("REFUSED", "Refused by recipient", false)));
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(parcelStatusService.changeStatus(eq(PARCEL), any()))
                .thenThrow(new ConflictException("A parcel that is CREATED cannot be changed to FAILED"));
        ReportFailureRequest request = new ReportFailureRequest("REFUSED", null);

        assertThatThrownBy(() -> service.reportFailure(PARCEL, COURIER, request))
                .isInstanceOf(ConflictException.class);
        verify(deliveryFailureRepository, never()).save(any());
    }
}

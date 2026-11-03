package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.repository.ParcelStatusRepository;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ParcelStatusServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private ParcelStatusRepository parcelStatusRepository;
    @Mock private ParcelStatusHistoryRepository statusHistoryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ParcelStatusService statusService;
    private final UUID courierUserId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        statusService = new ParcelStatusService(parcelRepository, parcelStatusRepository, statusHistoryRepository,
                eventPublisher);
    }

    private static ParcelStatus status(ParcelStatusCode code) {
        ParcelStatus status = new ParcelStatus();
        status.setStatusCode(code.name());
        return status;
    }

    private Parcel parcelWithStatus(ParcelStatusCode code) {
        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        parcel.setTrackingNumber("SD7KQ2MX9TRB4H");
        parcel.setMerchantId(merchantId);
        parcel.setCurrentStatus(status(code));
        when(parcelRepository.findByIdForUpdate(parcel.getId())).thenReturn(Optional.of(parcel));
        return parcel;
    }

    private void stubSaving(ParcelStatusCode next, long statusVersion) {
        when(parcelStatusRepository.getReferenceById(next.name())).thenReturn(status(next));
        when(statusHistoryRepository.saveAndFlush(any(ParcelStatusHistory.class))).thenAnswer(call -> {
            ParcelStatusHistory entry = call.getArgument(0);
            entry.setId(UUID.randomUUID());
            entry.setChangedAt(Instant.parse("2026-11-04T10:15:30Z"));
            return entry;
        });
        when(statusHistoryRepository.countByParcelId(any())).thenReturn(statusVersion);
    }

    @Test
    void movesTheParcelForwardRecordsHistoryAndPublishesAnEvent() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.CREATED);
        stubSaving(ParcelStatusCode.PICKED_UP, 2);

        StatusHistoryEntry entry = statusService.changeStatus(parcel.getId(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, " Collected from the shop "));

        assertThat(parcel.getCurrentStatus().getStatusCode()).isEqualTo("PICKED_UP");
        assertThat(entry.previousStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(entry.status()).isEqualTo(ParcelStatusCode.PICKED_UP);
        assertThat(entry.changedByUserId()).isEqualTo(courierUserId);
        assertThat(entry.note()).isEqualTo("Collected from the shop");
        assertThat(entry.parcelId()).isEqualTo(parcel.getId());

        ArgumentCaptor<ParcelStatusChangedEvent> published = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        ParcelStatusChangedEvent event = published.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo("PARCEL_STATUS_CHANGED");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.parcelId()).isEqualTo(parcel.getId());
        assertThat(event.trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
        assertThat(event.merchantId()).isEqualTo(merchantId);
        assertThat(event.previousStatus()).isEqualTo("CREATED");
        assertThat(event.newStatus()).isEqualTo("PICKED_UP");
        assertThat(event.statusVersion()).isEqualTo(2);
        assertThat(event.statusHistoryId()).isEqualTo(entry.id());
        assertThat(event.changedByUserId()).isEqualTo(courierUserId);
        assertThat(event.occurredAt()).isEqualTo(entry.changedAt());
    }

    @Test
    void deliveredParcelKeepsTheProof() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.OUT_FOR_DELIVERY);
        stubSaving(ParcelStatusCode.DELIVERED, 5);

        StatusHistoryEntry entry = statusService.changeStatus(parcel.getId(),
                StatusChange.delivered(courierUserId, " Kamala Silva ", "proofs/abc.jpg", null));

        assertThat(entry.recipientName()).isEqualTo("Kamala Silva");
        assertThat(entry.proofFileUrl()).isEqualTo("proofs/abc.jpg");
        assertThat(entry.failureReason()).isNull();
    }

    @Test
    void failedParcelKeepsTheReason() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.OUT_FOR_DELIVERY);
        stubSaving(ParcelStatusCode.FAILED, 5);

        StatusHistoryEntry entry = statusService.changeStatus(parcel.getId(),
                StatusChange.failed(courierUserId, "Recipient not at home", "Called twice"));

        assertThat(entry.status()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(entry.failureReason()).isEqualTo("Recipient not at home");
        assertThat(entry.note()).isEqualTo("Called twice");
    }

    @Test
    void rejectsAChangeTheDiagramDoesNotAllow() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.CREATED);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(),
                StatusChange.delivered(courierUserId, "Kamala Silva", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A parcel that is CREATED cannot be changed to DELIVERED");
        assertNothingSaved(parcel, ParcelStatusCode.CREATED);
    }

    @Test
    void endStatesCannotChange() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.DELIVERED);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(),
                StatusChange.failed(courierUserId, "Wrong address", null)))
                .isInstanceOf(ConflictException.class);
        assertNothingSaved(parcel, ParcelStatusCode.DELIVERED);
    }

    @Test
    void failedNeedsAReason() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.IN_TRANSIT);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(),
                StatusChange.failed(courierUserId, "   ", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A failure reason is required");
        assertNothingSaved(parcel, ParcelStatusCode.IN_TRANSIT);
    }

    @Test
    void deliveredNeedsTheRecipientsNameOrAPhoto() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.OUT_FOR_DELIVERY);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(),
                StatusChange.delivered(courierUserId, " ", null, "Left at the door")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Proof of delivery is required: the recipient's name or a photo");
        assertNothingSaved(parcel, ParcelStatusCode.OUT_FOR_DELIVERY);
    }

    @Test
    void deliveredWithOnlyAPhotoIsAccepted() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.OUT_FOR_DELIVERY);
        stubSaving(ParcelStatusCode.DELIVERED, 5);

        assertThat(statusService.changeStatus(parcel.getId(),
                StatusChange.delivered(courierUserId, null, "proofs/abc.jpg", null)).recipientName()).isNull();
    }

    @Test
    void rejectsAFailureReasonOnAnotherStatus() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.CREATED);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(), new StatusChange(
                ParcelStatusCode.PICKED_UP, courierUserId, null, null, null, "Shop closed")))
                .isInstanceOf(BadRequestException.class);
        assertNothingSaved(parcel, ParcelStatusCode.CREATED);
    }

    @Test
    void rejectsProofOnAnotherStatus() {
        Parcel parcel = parcelWithStatus(ParcelStatusCode.PICKED_UP);

        assertThatThrownBy(() -> statusService.changeStatus(parcel.getId(), new StatusChange(
                ParcelStatusCode.FAILED, courierUserId, null, "Kamala Silva", null, "Damaged")))
                .isInstanceOf(BadRequestException.class);
        assertNothingSaved(parcel, ParcelStatusCode.PICKED_UP);
    }

    @Test
    void unknownParcelIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(parcelRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statusService.changeStatus(unknown,
                StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, null)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(statusHistoryRepository, eventPublisher);
    }

    @Test
    void historyIsReturnedOldestFirst() {
        UUID parcelId = UUID.randomUUID();
        Parcel parcel = new Parcel();
        parcel.setId(parcelId);
        ParcelStatusHistory first = new ParcelStatusHistory();
        first.setParcel(parcel);
        first.setStatus(status(ParcelStatusCode.CREATED));
        ParcelStatusHistory second = new ParcelStatusHistory();
        second.setParcel(parcel);
        second.setPreviousStatus(status(ParcelStatusCode.CREATED));
        second.setStatus(status(ParcelStatusCode.PICKED_UP));
        when(parcelRepository.existsById(parcelId)).thenReturn(true);
        when(statusHistoryRepository.findByParcelIdOrderByChangedAtAsc(parcelId)).thenReturn(List.of(first, second));

        List<StatusHistoryEntry> history = statusService.getHistory(parcelId);

        assertThat(history).extracting(StatusHistoryEntry::status)
                .containsExactly(ParcelStatusCode.CREATED, ParcelStatusCode.PICKED_UP);
        assertThat(history.getFirst().previousStatus()).isNull();
        assertThat(history.get(1).previousStatus()).isEqualTo(ParcelStatusCode.CREATED);
    }

    @Test
    void historyOfAnUnknownParcelIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(parcelRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> statusService.getHistory(unknown)).isInstanceOf(NotFoundException.class);
    }

    private void assertNothingSaved(Parcel parcel, ParcelStatusCode unchanged) {
        assertThat(parcel.getCurrentStatus().getStatusCode()).isEqualTo(unchanged.name());
        verify(statusHistoryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }
}

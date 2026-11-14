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
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.DeliverRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.ProofPhoto;
import com.swiftdrop.delivery.model.DeliveryProof;
import com.swiftdrop.delivery.repository.DeliveryProofRepository;
import com.swiftdrop.delivery.storage.ProofStorage;
import com.swiftdrop.delivery.storage.StoredPhoto;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DeliveryProofServiceTest {

    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PARCEL = UUID.randomUUID();
    private static final byte[] UPLOAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Mock private DispatchApi dispatchApi;
    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;
    @Mock private DeliveryProofRepository deliveryProofRepository;
    @Mock private ProofStorage proofStorage;
    @InjectMocks private DeliveryProofService service;

    private static ParcelDetails parcel(ParcelStatusCode status) {
        AddressDetails address = new AddressDetails(UUID.randomUUID(), "Recipient", "+94771234567", "12 Galle Road",
                null, "Colombo", "Western", null, null);
        return new ParcelDetails(PARCEL, "SD1", CUSTOMER, null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, status, null, BigDecimal.TEN, "LKR", Instant.now(),
                Instant.now());
    }

    private static StatusHistoryEntry deliveredEntry(UUID entryId) {
        return new StatusHistoryEntry(entryId, PARCEL, ParcelStatusCode.OUT_FOR_DELIVERY, ParcelStatusCode.DELIVERED,
                COURIER, null, null, "Kasun Perera", null, Instant.now());
    }

    private static DeliverRequest withPhoto(String name, String note) {
        return new DeliverRequest(name, note, new MockMultipartFile("photo", "p.jpg", "image/jpeg", UPLOAD));
    }

    private void parcelIsAssignedAndDelivers(UUID entryId) {
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(parcelStatusService.changeStatus(eq(PARCEL), any())).thenReturn(deliveredEntry(entryId));
        when(parcelService.getParcel(PARCEL)).thenReturn(parcel(ParcelStatusCode.DELIVERED));
    }

    @Test
    void deliveringWithAPhotoStoresItRecordsTheStatusAndSavesTheProof() {
        UUID entryId = UUID.randomUUID();
        parcelIsAssignedAndDelivers(entryId);
        when(proofStorage.store(UPLOAD)).thenReturn(new StoredPhoto("2026/11/a.jpg", "image/jpeg", 1234, "ab12"));

        CourierDeliveryDetailResponse detail = service.deliver(PARCEL, COURIER,
                withPhoto("  Kasun Perera ", "Left with the guard"));

        ArgumentCaptor<DeliveryProof> saved = ArgumentCaptor.forClass(DeliveryProof.class);
        verify(deliveryProofRepository).save(saved.capture());
        DeliveryProof proof = saved.getValue();
        assertThat(proof.getId()).isNotNull();
        assertThat(proof.isNew()).isTrue();
        assertThat(proof.getParcelId()).isEqualTo(PARCEL);
        assertThat(proof.getStatusHistoryId()).isEqualTo(entryId);
        assertThat(proof.getCourierUserId()).isEqualTo(COURIER);
        assertThat(proof.getRecipientName()).isEqualTo("Kasun Perera");
        assertThat(proof.getPhotoStorageKey()).isEqualTo("2026/11/a.jpg");
        assertThat(proof.getPhotoContentType()).isEqualTo("image/jpeg");
        assertThat(proof.getPhotoSizeBytes()).isEqualTo(1234);
        assertThat(proof.getPhotoSha256()).isEqualTo("ab12");

        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(eq(PARCEL), change.capture());
        assertThat(change.getValue().newStatus()).isEqualTo(ParcelStatusCode.DELIVERED);
        assertThat(change.getValue().changedByUserId()).isEqualTo(COURIER);
        assertThat(change.getValue().recipientName()).isEqualTo("Kasun Perera");
        assertThat(change.getValue().proofFileUrl()).isEqualTo("/proofs/" + proof.getId() + "/photo");
        assertThat(change.getValue().note()).isEqualTo("Left with the guard");

        assertThat(detail.currentStatus()).isEqualTo(ParcelStatusCode.DELIVERED);
        assertThat(detail.allowedActions()).isEmpty();
    }

    @Test
    void deliveringWithoutAPhotoKeepsOnlyTheRecipientsName() {
        parcelIsAssignedAndDelivers(UUID.randomUUID());
        MockMultipartFile empty = new MockMultipartFile("photo", new byte[0]);

        service.deliver(PARCEL, COURIER, new DeliverRequest("Kasun Perera", null, empty));

        verifyNoInteractions(proofStorage);
        ArgumentCaptor<StatusChange> change = ArgumentCaptor.forClass(StatusChange.class);
        verify(parcelStatusService).changeStatus(eq(PARCEL), change.capture());
        assertThat(change.getValue().proofFileUrl()).isNull();
        ArgumentCaptor<DeliveryProof> saved = ArgumentCaptor.forClass(DeliveryProof.class);
        verify(deliveryProofRepository).save(saved.capture());
        assertThat(saved.getValue().hasPhoto()).isFalse();
        assertThat(saved.getValue().getPhotoSizeBytes()).isNull();
    }

    @Test
    void anotherCouriersParcelIsNotFoundAndNothingIsStored() {
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(false);
        DeliverRequest request = withPhoto("Kasun Perera", null);

        assertThatThrownBy(() -> service.deliver(PARCEL, COURIER, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(CourierDeliveryService.NOT_FOUND);
        verifyNoInteractions(proofStorage, parcelStatusService, deliveryProofRepository);
    }

    @Test
    void aPhotoThatIsNotAnImageIsRefusedBeforeTheStatusChanges() {
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(proofStorage.store(UPLOAD)).thenThrow(new BadRequestException("The photo must be a JPEG or PNG image"));
        DeliverRequest request = withPhoto("Kasun Perera", null);

        assertThatThrownBy(() -> service.deliver(PARCEL, COURIER, request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcelStatusService, deliveryProofRepository);
    }

    @Test
    void aParcelThatIsNotOutForDeliveryIsAConflictAndNoProofIsSaved() {
        when(dispatchApi.isAssignedTo(PARCEL, COURIER)).thenReturn(true);
        when(proofStorage.store(UPLOAD)).thenReturn(new StoredPhoto("2026/11/a.jpg", "image/jpeg", 10, "ab"));
        when(parcelStatusService.changeStatus(eq(PARCEL), any()))
                .thenThrow(new ConflictException("A parcel that is IN_TRANSIT cannot be changed to DELIVERED"));
        DeliverRequest request = withPhoto("Kasun Perera", null);

        // The storage deletes the file itself when the transaction rolls back.
        assertThatThrownBy(() -> service.deliver(PARCEL, COURIER, request)).isInstanceOf(ConflictException.class);
        verify(deliveryProofRepository, never()).save(any());
    }

    // ---- The photo endpoint ----------------------------------------------------------------------

    private static DeliveryProof storedProof(boolean withPhoto) {
        DeliveryProof proof = new DeliveryProof();
        proof.setId(UUID.randomUUID());
        proof.setParcelId(PARCEL);
        proof.setCourierUserId(COURIER);
        if (withPhoto) {
            proof.setPhotoStorageKey("2026/11/a.jpg");
            proof.setPhotoContentType("image/jpeg");
        }
        return proof;
    }

    private static AuthenticatedUser user(UUID id, Role role) {
        return new AuthenticatedUser(id.toString(), Set.of(role));
    }

    @Test
    void theCourierTheAdminAndTheOwningCustomerSeeThePhoto() {
        DeliveryProof proof = storedProof(true);
        when(deliveryProofRepository.findById(proof.getId())).thenReturn(Optional.of(proof));
        when(proofStorage.read("2026/11/a.jpg")).thenReturn(UPLOAD);
        when(parcelService.getParcel(PARCEL)).thenReturn(parcel(ParcelStatusCode.DELIVERED));

        for (AuthenticatedUser caller : List.of(user(COURIER, Role.COURIER),
                user(UUID.randomUUID(), Role.DISPATCHER_ADMIN), user(CUSTOMER, Role.CUSTOMER))) {
            ProofPhoto photo = service.photo(proof.getId(), caller);
            assertThat(photo.content()).isEqualTo(UPLOAD);
            assertThat(photo.contentType()).isEqualTo("image/jpeg");
        }
    }

    @Test
    void everyoneElseGetsNotFound() {
        DeliveryProof proof = storedProof(true);
        when(deliveryProofRepository.findById(proof.getId())).thenReturn(Optional.of(proof));
        when(parcelService.getParcel(PARCEL)).thenReturn(parcel(ParcelStatusCode.DELIVERED));

        for (AuthenticatedUser caller : List.of(user(UUID.randomUUID(), Role.COURIER),
                user(UUID.randomUUID(), Role.CUSTOMER), user(UUID.randomUUID(), Role.MERCHANT))) {
            assertThatThrownBy(() -> service.photo(proof.getId(), caller))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage(DeliveryProofService.PHOTO_NOT_FOUND);
        }
        verifyNoInteractions(proofStorage);
    }

    @Test
    void aProofWithoutAPhotoOrAnUnknownProofIsNotFound() {
        DeliveryProof noPhoto = storedProof(false);
        UUID unknown = UUID.randomUUID();
        when(deliveryProofRepository.findById(noPhoto.getId())).thenReturn(Optional.of(noPhoto));
        when(deliveryProofRepository.findById(unknown)).thenReturn(Optional.empty());
        AuthenticatedUser admin = user(UUID.randomUUID(), Role.DISPATCHER_ADMIN);

        assertThatThrownBy(() -> service.photo(noPhoto.getId(), admin)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.photo(unknown, admin)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(proofStorage);
    }
}

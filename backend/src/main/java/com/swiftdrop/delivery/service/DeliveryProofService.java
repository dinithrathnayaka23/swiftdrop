package com.swiftdrop.delivery.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.DeliverRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.ProofPhoto;
import com.swiftdrop.delivery.mapper.CourierDeliveryMapper;
import com.swiftdrop.delivery.model.DeliveryProof;
import com.swiftdrop.delivery.repository.DeliveryProofRepository;
import com.swiftdrop.delivery.storage.ProofStorage;
import com.swiftdrop.delivery.storage.StoredPhoto;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proof of delivery: marking a parcel DELIVERED with the recipient's name and an optional photo,
 * and showing that photo to the people allowed to see it.
 *
 * <p>Delivering is one transaction: ownership check, store the photo, move the parcel to DELIVERED
 * through {@link ParcelStatusService}, save the proof row. If anything fails the transaction rolls
 * back, the stored photo is deleted again and no status event is published. Dispatch ends the
 * assignment when it hears the event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryProofService {

    static final String PHOTO_NOT_FOUND = "Photo not found";

    private final DispatchApi dispatchApi;
    private final ParcelService parcelService;
    private final ParcelStatusService parcelStatusService;
    private final DeliveryProofRepository deliveryProofRepository;
    private final ProofStorage proofStorage;

    /** The URL the parcel history keeps for a proof photo (relative to {@code /api/v1}). */
    public static String photoUrl(UUID proofId) {
        return "/proofs/" + proofId + "/photo";
    }

    /**
     * Marks one of the courier's deliveries as DELIVERED.
     *
     * @return the delivery as it is after the change
     * @throws BadRequestException if the photo is not a readable JPEG/PNG (or too large)
     * @throws NotFoundException   if the parcel isn't assigned to this courier (or doesn't exist)
     * @throws ConflictException   if the parcel can't be delivered now (not out for delivery)
     */
    @Transactional
    public CourierDeliveryDetailResponse deliver(UUID parcelId, UUID courierUserId, DeliverRequest request) {
        if (!dispatchApi.isAssignedTo(parcelId, courierUserId)) {
            throw new NotFoundException(CourierDeliveryService.NOT_FOUND);
        }
        String recipientName = request.recipientName().trim();
        UUID proofId = UUID.randomUUID();
        // Stored first so a bad photo is refused before anything changes; a later failure deletes it.
        StoredPhoto photo = request.hasPhoto() ? proofStorage.store(bytes(request)) : null;

        StatusHistoryEntry entry = parcelStatusService.changeStatus(parcelId, StatusChange.delivered(courierUserId,
                recipientName, photo == null ? null : photoUrl(proofId), request.note()));

        DeliveryProof proof = new DeliveryProof();
        proof.setId(proofId);
        proof.setParcelId(parcelId);
        proof.setStatusHistoryId(entry.id());
        proof.setCourierUserId(courierUserId);
        proof.setRecipientName(recipientName);
        if (photo != null) {
            proof.setPhotoStorageKey(photo.storageKey());
            proof.setPhotoContentType(photo.contentType());
            proof.setPhotoSizeBytes(photo.sizeBytes());
            proof.setPhotoSha256(photo.sha256());
        }
        deliveryProofRepository.save(proof);

        log.info("Courier {} delivered parcel {} (proof {}, photo: {})", courierUserId, parcelId, proofId,
                photo != null);
        return CourierDeliveryMapper.toDetail(parcelService.getParcel(parcelId),
                parcelStatusService.getHistory(parcelId));
    }

    /**
     * The proof photo, for the admin, the courier who took it, or the customer whose parcel it is.
     * Anyone else, or a proof without a photo, gets "Photo not found" (404), so the photo's
     * existence isn't revealed.
     */
    public ProofPhoto photo(UUID proofId, AuthenticatedUser caller) {
        DeliveryProof proof = deliveryProofRepository.findById(proofId)
                .filter(DeliveryProof::hasPhoto)
                .filter(found -> maySee(found, caller))
                .orElseThrow(() -> new NotFoundException(PHOTO_NOT_FOUND));
        return new ProofPhoto(proofStorage.read(proof.getPhotoStorageKey()), proof.getPhotoContentType());
    }

    private boolean maySee(DeliveryProof proof, AuthenticatedUser caller) {
        if (caller.hasRole(Role.DISPATCHER_ADMIN)) {
            return true;
        }
        UUID callerId = UUID.fromString(caller.userId());
        if (callerId.equals(proof.getCourierUserId())) {
            return true;
        }
        return caller.hasRole(Role.CUSTOMER)
                && Objects.equals(parcelService.getParcel(proof.getParcelId()).customerUserId(), callerId);
    }

    private static byte[] bytes(DeliverRequest request) {
        try {
            return request.photo().getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded photo", e);
        }
    }
}

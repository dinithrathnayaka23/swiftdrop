package com.swiftdrop.parcel.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.mapper.ParcelMapper;
import com.swiftdrop.parcel.model.Address;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.AddressRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.repository.ParcelStatusRepository;
import com.swiftdrop.parcel.repository.StatusCount;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the parcel module for creating and finding parcels. Other modules use this, never
 * the parcel repositories or entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelService {

    /** Attempts at a tracking number that is not taken yet; a clash is already very unlikely. */
    static final int TRACKING_NUMBER_ATTEMPTS = 5;

    /** Order used when the caller's page request has no sort: newest first. */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ParcelRepository parcelRepository;
    private final ParcelStatusRepository parcelStatusRepository;
    private final ParcelStatusHistoryRepository statusHistoryRepository;
    private final AddressRepository addressRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;

    /** Stored form of a tracking number: trimmed and upper case, so look-ups ignore case. */
    public static String normalizeTrackingNumber(String trackingNumber) {
        return trackingNumber.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Saves a new parcel with status CREATED, its two addresses and the first status history entry,
     * in one transaction. Runs inside the caller's transaction when there is one.
     *
     * @throws ConflictException if the merchant already has a parcel with this external order reference
     */
    @Transactional
    public ParcelDetails createParcel(NewParcel newParcel) {
        String externalOrderReference = trimToNull(newParcel.externalOrderReference());
        if (externalOrderReference != null && parcelRepository.existsByMerchantIdAndExternalOrderReference(
                newParcel.merchantId(), externalOrderReference)) {
            throw ConflictException.forField("externalOrderReference",
                    "An order with this reference already exists");
        }

        ParcelStatus created = parcelStatusRepository.getReferenceById(ParcelStatusCode.CREATED.name());

        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(newTrackingNumber());
        parcel.setCustomerUserId(newParcel.customerUserId());
        parcel.setMerchantId(newParcel.merchantId());
        parcel.setExternalOrderReference(externalOrderReference);
        parcel.setPickupAddress(addressRepository.save(toAddress(newParcel.pickupAddress())));
        parcel.setDropoffAddress(addressRepository.save(toAddress(newParcel.dropoffAddress())));
        parcel.setParcelType(newParcel.parcelType());
        parcel.setWeightKg(newParcel.weightKg());
        parcel.setLengthCm(newParcel.lengthCm());
        parcel.setWidthCm(newParcel.widthCm());
        parcel.setHeightCm(newParcel.heightCm());
        parcel.setContentDescription(trimToNull(newParcel.contentDescription()));
        parcel.setDeliveryInstructions(trimToNull(newParcel.deliveryInstructions()));
        parcel.setCurrentStatus(created);
        parcel.setPricingRuleId(newParcel.pricingRuleId());
        parcel.setQuotedFee(newParcel.quotedFee());
        Parcel saved = parcelRepository.saveAndFlush(parcel);

        ParcelStatusHistory firstEntry = new ParcelStatusHistory();
        firstEntry.setParcel(saved);
        firstEntry.setStatus(created);
        // The booking customer made the change; merchant API orders are system changes (null).
        firstEntry.setChangedByUserId(newParcel.customerUserId());
        statusHistoryRepository.save(firstEntry);

        log.info("Parcel {} created", saved.getId());
        return ParcelMapper.toDetails(saved);
    }

    /** @throws NotFoundException if there is no parcel with this id */
    @Transactional(readOnly = true)
    public ParcelDetails getParcel(UUID parcelId) {
        return parcelRepository.findWithDetailsById(parcelId)
                .map(ParcelMapper::toDetails)
                .orElseThrow(() -> new NotFoundException("Parcel not found"));
    }

    /**
     * The parcels with these ids, in no particular order, in one query (for example the parcels of
     * a courier's assignments). Unknown ids are skipped.
     */
    @Transactional(readOnly = true)
    public List<ParcelDetails> findAllByIds(Collection<UUID> parcelIds) {
        if (parcelIds.isEmpty()) {
            return List.of();
        }
        return parcelRepository.findWithDetailsByIdIn(parcelIds).stream()
                .map(ParcelMapper::toDetails)
                .toList();
    }

    /** Case and surrounding spaces in {@code trackingNumber} are ignored. */
    @Transactional(readOnly = true)
    public Optional<ParcelDetails> findByTrackingNumber(String trackingNumber) {
        return parcelRepository.findByTrackingNumber(normalizeTrackingNumber(trackingNumber))
                .map(ParcelMapper::toDetails);
    }

    /** A merchant's order by the merchant's own order reference (surrounding spaces are ignored). */
    @Transactional(readOnly = true)
    public Optional<ParcelDetails> findByMerchantOrderReference(UUID merchantId, String externalOrderReference) {
        String reference = trimToNull(externalOrderReference);
        if (reference == null) {
            return Optional.empty();
        }
        return parcelRepository.findByMerchantIdAndExternalOrderReference(merchantId, reference)
                .map(ParcelMapper::toDetails);
    }

    /** The customer's own bookings, newest first unless {@code pageable} has a sort. */
    @Transactional(readOnly = true)
    public Page<ParcelDetails> listByCustomer(UUID customerUserId, Pageable pageable) {
        return parcelRepository.findByCustomerUserId(customerUserId, withDefaultSort(pageable))
                .map(ParcelMapper::toDetails);
    }

    /** The merchant's orders, newest first unless {@code pageable} has a sort. */
    @Transactional(readOnly = true)
    public Page<ParcelDetails> listByMerchant(UUID merchantId, Pageable pageable) {
        return parcelRepository.findByMerchantId(merchantId, withDefaultSort(pageable))
                .map(ParcelMapper::toDetails);
    }

    /**
     * All parcels that currently have this status, newest first unless {@code pageable} has a sort.
     * For example CREATED parcels waiting for a courier, or FAILED ones to follow up.
     */
    @Transactional(readOnly = true)
    public Page<ParcelDetails> listByStatus(ParcelStatusCode status, Pageable pageable) {
        return parcelRepository.findByCurrentStatusStatusCode(status.name(), withDefaultSort(pageable))
                .map(ParcelMapper::toDetails);
    }

    /** Number of parcels per status, for every status (0 when there are none), in lifecycle order. */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> countByStatus() {
        return toStatusMap(parcelRepository.countByStatus());
    }

    /** Like {@link #countByStatus()}, for one merchant's orders only. */
    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> countByStatusForMerchant(UUID merchantId) {
        return toStatusMap(parcelRepository.countByStatusForMerchant(merchantId));
    }

    private static Map<ParcelStatusCode, Long> toStatusMap(List<StatusCount> counts) {
        Map<ParcelStatusCode, Long> byStatus = new EnumMap<>(ParcelStatusCode.class);
        for (ParcelStatusCode status : ParcelStatusCode.values()) {
            byStatus.put(status, 0L);
        }
        for (StatusCount count : counts) {
            byStatus.put(ParcelStatusCode.valueOf(count.statusCode()), count.count());
        }
        return Collections.unmodifiableMap(byStatus);
    }

    private String newTrackingNumber() {
        for (int attempt = 0; attempt < TRACKING_NUMBER_ATTEMPTS; attempt++) {
            String candidate = trackingNumberGenerator.next();
            if (!parcelRepository.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free tracking number");
    }

    private static Address toAddress(NewAddress newAddress) {
        Address address = new Address();
        address.setContactName(newAddress.contactName().trim());
        address.setContactPhone(newAddress.contactPhone().trim());
        address.setAddressLine1(newAddress.addressLine1().trim());
        address.setAddressLine2(trimToNull(newAddress.addressLine2()));
        address.setCity(newAddress.city().trim());
        address.setDistrict(newAddress.district().trim());
        address.setPostalCode(trimToNull(newAddress.postalCode()));
        address.setZoneId(newAddress.zoneId());
        return address;
    }

    private static Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(DEFAULT_SORT);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

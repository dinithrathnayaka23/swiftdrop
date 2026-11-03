package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.Parcel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read methods load the addresses and status in the same query, as every caller maps them. */
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Optional<Parcel> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Page<Parcel> findByCustomerUserId(UUID customerUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Page<Parcel> findByMerchantId(UUID merchantId, Pageable pageable);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByMerchantIdAndExternalOrderReference(UUID merchantId, String externalOrderReference);
}

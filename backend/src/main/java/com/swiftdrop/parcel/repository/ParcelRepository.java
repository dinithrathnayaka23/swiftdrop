package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.Parcel;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read methods load the addresses and status in the same query, as every caller maps them. */
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Optional<Parcel> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    List<Parcel> findWithDetailsByIdIn(Collection<UUID> ids);

    /** Locks the parcel row until the transaction ends, so two status changes cannot overlap. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p join fetch p.currentStatus where p.id = :id")
    Optional<Parcel> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Optional<Parcel> findByMerchantIdAndExternalOrderReference(UUID merchantId, String externalOrderReference);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Page<Parcel> findByCustomerUserId(UUID customerUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Page<Parcel> findByMerchantId(UUID merchantId, Pageable pageable);

    @EntityGraph(attributePaths = {"pickupAddress", "dropoffAddress", "currentStatus"})
    Page<Parcel> findByCurrentStatusStatusCode(String statusCode, Pageable pageable);

    @Query("select new com.swiftdrop.parcel.repository.StatusCount(p.currentStatus.statusCode, count(p)) "
            + "from Parcel p group by p.currentStatus.statusCode")
    List<StatusCount> countByStatus();

    @Query("select new com.swiftdrop.parcel.repository.StatusCount(p.currentStatus.statusCode, count(p)) "
            + "from Parcel p where p.merchantId = :merchantId group by p.currentStatus.statusCode")
    List<StatusCount> countByStatusForMerchant(@Param("merchantId") UUID merchantId);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByMerchantIdAndExternalOrderReference(UUID merchantId, String externalOrderReference);
}

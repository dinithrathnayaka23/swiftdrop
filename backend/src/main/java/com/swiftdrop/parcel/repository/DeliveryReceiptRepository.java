package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.DeliveryReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, UUID> {

    Optional<DeliveryReceipt> findByParcelId(UUID parcelId);

    /**
     * Records the parcel's receipt unless it already has one. Two first downloads at the same time
     * both succeed: the second insert is skipped instead of breaking the unique parcel_id rule.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO delivery_receipts (parcel_id, receipt_number, document_url)
            VALUES (:parcelId, :receiptNumber, :documentUrl)
            ON CONFLICT (parcel_id) DO NOTHING""")
    void insertIfAbsent(@Param("parcelId") UUID parcelId, @Param("receiptNumber") String receiptNumber,
                        @Param("documentUrl") String documentUrl);
}

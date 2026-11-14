package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.model.DeliveryProof;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the delivery module. */
public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {
}

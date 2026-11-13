package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.model.DeliveryFailure;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the delivery module. */
public interface DeliveryFailureRepository extends JpaRepository<DeliveryFailure, UUID> {
}

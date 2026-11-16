package com.swiftdrop.config.repository;

import com.swiftdrop.config.model.DeliveryZone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Private to the config module: other modules use {@code ZoneApi}. Filters: {@link ZoneSpecifications}. */
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID>, JpaSpecificationExecutor<DeliveryZone> {

    boolean existsByZoneNameIgnoreCase(String zoneName);

    boolean existsByZoneNameIgnoreCaseAndIdNot(String zoneName, UUID id);
}

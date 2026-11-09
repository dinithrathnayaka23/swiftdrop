package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.Courier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Private to the dispatch module: other modules use {@code DispatchApi}. Filters: {@link CourierSpecifications}. */
public interface CourierRepository extends JpaRepository<Courier, UUID>, JpaSpecificationExecutor<Courier> {

    Optional<Courier> findByUserId(UUID userId);

    /** Registrations are stored upper-case with single spaces, so this is an exact match. */
    boolean existsByVehicleRegistration(String vehicleRegistration);

    boolean existsByVehicleRegistrationAndIdNot(String vehicleRegistration, UUID id);

    /** Active couriers per availability; an availability nobody has is missing from the result. */
    @Query("""
            select new com.swiftdrop.dispatch.repository.AvailabilityCount(c.availabilityStatus, count(c))
            from Courier c
            where c.active = true
            group by c.availabilityStatus
            """)
    List<AvailabilityCount> countActiveByAvailability();
}

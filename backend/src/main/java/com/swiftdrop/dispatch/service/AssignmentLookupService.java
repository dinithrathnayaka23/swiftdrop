package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.service.DispatchStateLookup;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the questions other modules ask about assignments: the parcel module's
 * {@link DispatchStateLookup} (Assigned / Unassigned tabs) and the {@link DispatchApi} (courier ownership).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentLookupService implements DispatchStateLookup, DispatchApi {

    private final DeliveryAssignmentRepository assignments;
    private final CourierRepository couriers;

    @Override
    public Set<UUID> parcelsWithOpenAssignment() {
        return Set.copyOf(assignments.findOpenParcelIds());
    }

    @Override
    public Optional<UUID> findCourierId(UUID courierUserId) {
        return couriers.findByUserId(courierUserId).map(Courier::getId);
    }

    @Override
    public boolean isAssignedTo(UUID parcelId, UUID courierUserId) {
        return assignments.existsByParcelIdAndCourier_UserIdAndEndedAtIsNull(parcelId, courierUserId);
    }

    @Override
    public List<UUID> openParcelIds(UUID courierUserId) {
        return assignments.findOpenParcelIdsByCourierUserId(courierUserId);
    }
}

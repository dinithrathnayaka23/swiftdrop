package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.dispatch.repository.OpenAssignmentCourier;
import com.swiftdrop.tracking.service.CourierLookup;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tracking module's {@link CourierLookup}: the name of the courier holding each parcel's open
 * assignment, for the admin Deliveries Tracking board.
 *
 * <p>One query finds the open assignments of all the parcels; each distinct courier's account is
 * then read once (UserApi can only look up one account at a time, and a board page has far fewer
 * couriers than parcels).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierNameLookupService implements CourierLookup {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final UserApi userApi;

    @Override
    public Map<UUID, String> currentCourierNames(Collection<UUID> parcelIds) {
        if (parcelIds == null || parcelIds.isEmpty()) {
            return Map.of();
        }
        List<OpenAssignmentCourier> open = assignmentRepository.findOpenCouriersByParcelIds(Set.copyOf(parcelIds));
        Map<UUID, String> names = open.stream().map(OpenAssignmentCourier::courierUserId).distinct()
                .map(userApi::findUser).flatMap(Optional::stream)
                .collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
        Map<UUID, String> byParcel = new HashMap<>();
        for (OpenAssignmentCourier row : open) {
            String name = names.get(row.courierUserId());
            if (name != null) {
                byParcel.put(row.parcelId(), name);
            }
        }
        return byParcel;
    }
}

package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.ActiveDeliveryResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The dispatcher's live view: deliveries under way and (next) the Operations Overview numbers. */
@Service
@RequiredArgsConstructor
public class OperationsService {

    /** Newest assignment first. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "assignedAt").and(Sort.by("id"));

    private final DeliveryAssignmentRepository assignmentRepository;
    private final ParcelService parcelService;
    private final UserApi userApi;

    /**
     * Open IN_PROGRESS assignments (couriers with the parcel), newest first, with their parcels loaded
     * in one call and each distinct courier's name read once.
     */
    @Transactional(readOnly = true)
    public Page<ActiveDeliveryResponse> activeDeliveries(int page, int size) {
        Page<DeliveryAssignment> found = assignmentRepository.findByEndedAtIsNullAndAssignmentStatus(
                AssignmentStatus.IN_PROGRESS, PageRequest.of(page, size, NEWEST_FIRST));
        if (found.isEmpty()) {
            return found.map(assignment -> null);
        }
        Map<UUID, ParcelDetails> parcels = parcelService.findAllByIds(
                        found.getContent().stream().map(DeliveryAssignment::getParcelId).toList()).stream()
                .collect(Collectors.toMap(ParcelDetails::id, Function.identity()));
        Map<UUID, String> names = namesOf(found.getContent().stream()
                .map(assignment -> assignment.getCourier().getUserId()).collect(Collectors.toSet()));
        return found.map(assignment -> {
            ParcelDetails parcel = parcels.get(assignment.getParcelId());
            return new ActiveDeliveryResponse(
                    assignment.getParcelId(),
                    parcel == null ? null : parcel.trackingNumber(),
                    assignment.getCourier().getId(),
                    names.get(assignment.getCourier().getUserId()),
                    parcel == null || parcel.dropoffAddress() == null ? null : parcel.dropoffAddress().city(),
                    parcel == null ? null : parcel.currentStatus(),
                    assignment.getAssignedAt());
        });
    }

    /** Account names, one UserApi call per account (it has no batch look-up); unknown accounts left out. */
    private Map<UUID, String> namesOf(Collection<UUID> userIds) {
        return userIds.stream().map(userApi::findUser).flatMap(Optional::stream)
                .collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
    }
}

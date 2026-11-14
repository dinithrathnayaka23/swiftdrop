package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.ActiveDeliveryResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.dto.response.OverviewResponse;
import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import com.swiftdrop.dispatch.repository.CourierDayLoad;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

/** The dispatcher's live view: deliveries under way and the Operations Overview numbers. */
@Service
@RequiredArgsConstructor
public class OperationsService {

    /** Newest assignment first. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "assignedAt").and(Sort.by("id"));

    /** Couriers shown in the overview's workload table. */
    static final int WORKLOAD_SIZE = 5;

    private final DeliveryAssignmentRepository assignmentRepository;
    private final ParcelService parcelService;
    private final UserApi userApi;
    private final CourierService courierService;
    private final SystemSettingsService settingsService;

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

    // ---- Overview --------------------------------------------------------------------------------

    /** The Operations Overview for today (Sri Lanka time). Definitions: docs/dispatch.md. */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        return overview(Instant.now());
    }

    /** As {@link #overview()}, with "now" given (tests use it for the midnight boundary). */
    OverviewResponse overview(Instant now) {
        Instant startOfDay = SriLankaDay.startOfDay(now);
        Map<ParcelStatusCode, Long> byStatus = parcelService.countByStatus();
        long openAssigned = assignmentRepository.countByEndedAtIsNullAndAssignmentStatus(AssignmentStatus.ASSIGNED);
        // Same meaning as the Unassigned tab; an open ASSIGNED assignment whose parcel already moved on
        // (event still on its way) could push it below 0 for a moment.
        long unassigned = Math.max(0, count(byStatus, ParcelStatusCode.CREATED) - openAssigned);
        long pickedUpOrInTransit = count(byStatus, ParcelStatusCode.PICKED_UP) + count(byStatus,
                ParcelStatusCode.IN_TRANSIT);
        long outForDelivery = count(byStatus, ParcelStatusCode.OUT_FOR_DELIVERY);
        long completedToday = assignmentRepository.countByAssignmentStatusAndEndedAtGreaterThanEqual(
                AssignmentStatus.COMPLETED, startOfDay);
        long failedToday = assignmentRepository.countByAssignmentStatusAndEndedAtGreaterThanEqual(
                AssignmentStatus.FAILED, startOfDay);
        CourierSummaryResponse couriers = courierService.summary();

        OverviewResponse.Kpis kpis = new OverviewResponse.Kpis(unassigned, pickedUpOrInTransit + outForDelivery,
                couriers.online(), couriers.onBreak(), couriers.total(), completedToday, failedToday,
                count(byStatus, ParcelStatusCode.FAILED_ATTEMPT));
        OverviewResponse.TodayStatus today = new OverviewResponse.TodayStatus(completedToday, pickedUpOrInTransit,
                outForDelivery, failedToday);
        return new OverviewResponse(now.atZone(SriLankaDay.ZONE).toLocalDate(),
                settingsService.current().failedDeliveryAction(), kpis, today, busiestCouriers(startOfDay));
    }

    /**
     * Up to {@value #WORKLOAD_SIZE} active couriers, busiest today first, then by name. One query picks
     * them (ties at the cut-off go to the oldest courier record: names live in the user module), then
     * one name look-up each.
     */
    private List<OverviewResponse.CourierLoad> busiestCouriers(Instant startOfDay) {
        List<CourierDayLoad> loads = assignmentRepository.findBusiestActiveCouriers(startOfDay,
                PageRequest.of(0, WORKLOAD_SIZE));
        Map<UUID, String> names = namesOf(loads.stream().map(CourierDayLoad::userId).toList());
        return loads.stream()
                .map(load -> new OverviewResponse.CourierLoad(load.courierId(), names.get(load.userId()),
                        load.todaysTotal(), load.completedToday(), load.inProgress(), load.pending(),
                        load.availability()))
                .sorted(Comparator.comparingLong(OverviewResponse.CourierLoad::todaysTotal).reversed()
                        .thenComparing(OverviewResponse.CourierLoad::name,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private static long count(Map<ParcelStatusCode, Long> byStatus, ParcelStatusCode status) {
        return byStatus.getOrDefault(status, 0L);
    }

    /** Account names, one UserApi call per account (it has no batch look-up); unknown accounts left out. */
    private Map<UUID, String> namesOf(Collection<UUID> userIds) {
        return userIds.stream().map(userApi::findUser).flatMap(Optional::stream)
                .collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
    }
}

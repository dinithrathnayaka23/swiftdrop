package com.swiftdrop.dispatch.mapper;

import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.dto.response.ServiceAreaRef;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.user.api.UserSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CourierMapper {

    /** By name (ignoring case), zones without a name last. */
    private static final Comparator<ServiceAreaRef> BY_NAME = Comparator.comparing(ServiceAreaRef::name,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(ServiceAreaRef::id);

    private CourierMapper() {
    }

    /**
     * Reads the courier's service areas: call it inside the transaction that loaded the courier.
     *
     * @param zoneNames names of (at least) this courier's zones, from {@code ZoneApi.zoneNames}; a
     *                  missing name is {@code null}
     */
    public static CourierResponse toResponse(Courier courier, UserSummary user, CourierWorkload workload,
                                             Map<UUID, String> zoneNames) {
        Set<UUID> zoneIds = Set.copyOf(courier.getServiceAreaIds());
        List<ServiceAreaRef> serviceAreas = zoneIds.stream()
                .map(id -> new ServiceAreaRef(id, zoneNames.get(id))).sorted(BY_NAME).toList();
        return new CourierResponse(
                courier.getId(),
                courier.getUserId(),
                user.fullName(),
                user.email(),
                user.phone(),
                courier.getVehicleType(),
                courier.getVehicleRegistration(),
                courier.getAvailabilityStatus(),
                courier.isActive(),
                user.accountStatus(),
                zoneIds,
                serviceAreas,
                workload,
                courier.getCreatedAt());
    }
}

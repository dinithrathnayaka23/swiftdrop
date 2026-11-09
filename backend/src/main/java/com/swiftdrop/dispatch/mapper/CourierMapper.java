package com.swiftdrop.dispatch.mapper;

import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.user.api.UserSummary;
import java.util.Set;

public final class CourierMapper {

    private CourierMapper() {
    }

    /** Reads the courier's service areas: call it inside the transaction that loaded the courier. */
    public static CourierResponse toResponse(Courier courier, UserSummary user, CourierWorkload workload) {
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
                Set.copyOf(courier.getServiceAreaIds()),
                workload,
                courier.getCreatedAt());
    }
}

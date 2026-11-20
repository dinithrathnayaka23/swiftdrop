package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.api.CourierProfile;
import com.swiftdrop.dispatch.api.CourierSelfApi;
import com.swiftdrop.dispatch.api.CourierStatus;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.ServiceAreaRef;
import com.swiftdrop.dispatch.model.CourierAvailability;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CourierSelfApi}: the courier's own profile and availability, for the courier app (Member 7).
 * A thin layer over {@link CourierService}, which keeps all the courier rules.
 */
@Service
@RequiredArgsConstructor
public class CourierSelfService implements CourierSelfApi {

    private final DispatchApi dispatchApi;
    private final CourierService courierService;

    @Override
    @Transactional(readOnly = true)
    public Optional<CourierProfile> profile(UUID courierUserId) {
        return dispatchApi.findCourierId(courierUserId).map(courierService::get).map(CourierSelfService::toProfile);
    }

    @Override
    @Transactional
    public Optional<CourierProfile> changeStatus(UUID courierUserId, CourierStatus status) {
        CourierAvailability availability = CourierAvailability.valueOf(status.name());
        return dispatchApi.findCourierId(courierUserId)
                .map(courierId -> courierService.changeAvailability(courierId, availability))
                .map(CourierSelfService::toProfile);
    }

    static CourierProfile toProfile(CourierResponse courier) {
        return new CourierProfile(
                courier.id(),
                courier.fullName(),
                courier.email(),
                courier.phone(),
                courier.vehicleType(),
                courier.vehicleRegistration(),
                CourierStatus.valueOf(courier.availability().name()),
                courier.active(),
                courier.serviceAreas() == null ? List.of()
                        : courier.serviceAreas().stream().map(ServiceAreaRef::name).toList(),
                courier.workload() == null ? 0 : courier.workload().assigned(),
                courier.workload() == null ? 0 : courier.workload().inProgress(),
                courier.workload() == null ? 0 : courier.workload().completedToday());
    }
}

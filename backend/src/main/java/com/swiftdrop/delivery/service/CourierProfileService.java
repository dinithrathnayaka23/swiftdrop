package com.swiftdrop.delivery.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.api.CourierProfile;
import com.swiftdrop.dispatch.api.CourierSelfApi;
import com.swiftdrop.dispatch.api.CourierStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The courier's profile card: who they are, their vehicle and areas, and their availability. The
 * data and the rules belong to dispatch ({@link CourierSelfApi}); this only answers the courier app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierProfileService {

    static final String NO_PROFILE = "Courier profile not found";

    private final CourierSelfApi courierSelfApi;

    /** @throws NotFoundException if the account has no courier profile */
    public CourierProfile profile(UUID courierUserId) {
        return courierSelfApi.profile(courierUserId).orElseThrow(() -> new NotFoundException(NO_PROFILE));
    }

    /**
     * @throws NotFoundException if the account has no courier profile
     * @throws ConflictException if a deactivated courier asks to go online
     */
    public CourierProfile changeStatus(UUID courierUserId, CourierStatus status) {
        CourierProfile profile = courierSelfApi.changeStatus(courierUserId, status)
                .orElseThrow(() -> new NotFoundException(NO_PROFILE));
        log.info("Courier {} is now {}", profile.courierId(), status);
        return profile;
    }
}

package com.swiftdrop.dispatch.api;

import com.swiftdrop.common.exception.ConflictException;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the dispatch module for a courier looking at themselves (the courier app's profile
 * card). The courier is identified by their sign-in account ({@code AuthenticatedUser.userId()}).
 *
 * <p>Added by Member 7 for the courier app; it only delegates to dispatch's own services, so the
 * rules (e.g. a deactivated courier can't go online) stay in one place.
 */
public interface CourierSelfApi {

    /** The courier's profile, or empty if this account has no courier profile. */
    Optional<CourierProfile> profile(UUID courierUserId);

    /**
     * Sets the courier's own availability. Going on break or offline keeps their open deliveries.
     *
     * @return the updated profile, or empty if this account has no courier profile
     * @throws ConflictException if the courier is deactivated and asks to go online
     */
    Optional<CourierProfile> changeStatus(UUID courierUserId, CourierStatus status);
}

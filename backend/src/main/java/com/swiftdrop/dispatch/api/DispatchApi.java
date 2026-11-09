package com.swiftdrop.dispatch.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the dispatch module. Other modules (the courier app, merchant portal, ...) ask who a
 * parcel is assigned to through this interface and never use dispatch's repositories or entities.
 *
 * <p>An assignment is <em>open</em> until it ends: completed, reassigned or cancelled.
 * A courier is identified by the id of their sign-in account ({@code users.user_id}), which is what
 * {@code AuthenticatedUser.userId()} holds.
 */
public interface DispatchApi {

    /** The courier profile ({@code couriers.courier_id}) of this account, or empty if it has none. */
    Optional<UUID> findCourierId(UUID courierUserId);

    /**
     * Whether the parcel has an open assignment to this courier. The ownership check before a
     * courier reads or changes a parcel: a courier must not touch another courier's deliveries.
     */
    boolean isAssignedTo(UUID parcelId, UUID courierUserId);

    /** The parcels in this courier's open assignments, oldest assignment first. */
    List<UUID> openParcelIds(UUID courierUserId);
}

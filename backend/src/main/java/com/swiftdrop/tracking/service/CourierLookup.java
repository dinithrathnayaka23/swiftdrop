package com.swiftdrop.tracking.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Courier names for the admin Deliveries Tracking board.
 *
 * <p>The tracking module owns this interface so it never reads {@code couriers} or
 * {@code delivery_assignments} itself (same pattern as {@code parcel.service.DispatchStateLookup}).
 * <b>Implemented later as a Spring bean</b>, either by the dispatch module (Member 6) or by a small
 * adapter in this module that calls Member 6's CourierApi. Until a bean exists, the board shows
 * "—" in the courier column; inject it with {@code ObjectProvider<CourierLookup>} and use
 * {@code getIfAvailable()}.
 */
public interface CourierLookup {

    /**
     * Display names of the couriers currently assigned to these parcels, keyed by parcel id.
     * Parcels without an open assignment are left out. Unknown ids are skipped.
     */
    Map<UUID, String> currentCourierNames(Collection<UUID> parcelIds);
}

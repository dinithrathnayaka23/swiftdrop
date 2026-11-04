package com.swiftdrop.parcel.service;

import java.util.Set;
import java.util.UUID;

/**
 * Tells the parcel module which parcels already have a courier, for the admin Parcels screen
 * ("Unassigned" / "Assigned").
 *
 * <p><b>Implemented by the dispatch module (Member 6)</b> as a Spring bean. The parcel module owns
 * the interface so it never reads {@code delivery_assignments} itself. Until a bean exists, every
 * CREATED parcel counts as unassigned.
 */
public interface DispatchStateLookup {

    /**
     * Ids of the parcels that have an open assignment ({@code ended_at IS NULL}). The parcel module
     * only uses the ones whose status is still CREATED.
     */
    Set<UUID> parcelsWithOpenAssignment();
}

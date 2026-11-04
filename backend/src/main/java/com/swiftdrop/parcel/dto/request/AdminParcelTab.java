package com.swiftdrop.parcel.dto.request;

/** Tabs of the admin Parcels screen. */
public enum AdminParcelTab {
    ALL,
    /** CREATED parcels without a courier (dispatch module). */
    UNASSIGNED,
    /** CREATED parcels already given to a courier (dispatch module). */
    ASSIGNED,
    /** PICKED_UP, IN_TRANSIT and OUT_FOR_DELIVERY. */
    IN_TRANSIT,
    DELIVERED,
    FAILED
}

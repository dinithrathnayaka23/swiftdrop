package com.swiftdrop.parcel.model;

/** What a parcel contains, chosen when it is booked. Column {@code parcels.parcel_type} (see {@code V4_002}). */
public enum ParcelType {
    /** Letters and papers. */
    DOCUMENT,
    /** General goods with no special handling. */
    PARCEL,
    /** Breakable items, such as glass or ceramics. */
    FRAGILE,
    /** Phones, computers and other devices. */
    ELECTRONICS,
    /** Food, flowers and other items that spoil. */
    PERISHABLE
}

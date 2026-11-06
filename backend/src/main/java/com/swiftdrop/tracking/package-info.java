/**
 * <b>tracking module.</b> Public parcel tracking (no sign-in) and the admin Deliveries Tracking
 * board. Reads parcels only through {@code parcel.service.ParcelService} and
 * {@code ParcelStatusService}; owns no tables.
 *
 * <p>Owner: Member 5 (Public Tracking &amp; Real-time Updates). See docs/tracking.md.
 *
 * <p>Privacy rule: public responses contain only the tracking number, status codes, timestamps,
 * from/to city and weight. Never names, phone numbers, street addresses, notes, failure reasons
 * or proof.
 *
 * <p>Layout: {@code controller}, {@code service}, {@code dto.response}, {@code mapper},
 * {@code config}.
 */
package com.swiftdrop.tracking;

package com.swiftdrop.parcel.repository;

/** One row of a "parcels per status" count query. */
public record StatusCount(String statusCode, Long count) {
}

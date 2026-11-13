package com.swiftdrop.dispatch.repository;

import java.util.UUID;

/** A parcel and the account ({@code users.user_id}) of the courier holding its open assignment. */
public record OpenAssignmentCourier(UUID parcelId, UUID courierUserId) {
}

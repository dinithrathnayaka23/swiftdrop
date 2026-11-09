package com.swiftdrop.dispatch.dto.response;

/** The cards above the Couriers list. Counts active couriers only; {@code total = online + onBreak + offline}. */
public record CourierSummaryResponse(long total, long online, long onBreak, long offline) {
}

package com.swiftdrop.delivery.dto.response;

/** One choice on the Failed Delivery form. */
public record FailureReasonResponse(String code, String label, boolean noteRequired) {
}

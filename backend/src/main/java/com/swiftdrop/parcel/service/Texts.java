package com.swiftdrop.parcel.service;

/** Text clean-up shared by the parcel services. */
final class Texts {

    private Texts() {
    }

    /** Trims the value; blank or {@code null} becomes {@code null}, so optional fields are stored as NULL. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

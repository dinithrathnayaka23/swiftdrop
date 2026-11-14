package com.swiftdrop.config.service;

import java.util.Locale;

/**
 * How zone names and places are cleaned before they are stored or compared: trimmed with single
 * spaces; postal codes also upper case. Comparisons ignore case (the queries use {@code upper()}).
 */
final class Places {

    private Places() {
    }

    /** Trimmed, single spaces; {@code null} or blank gives {@code null}. */
    static String text(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Like {@link #text}, upper case. */
    static String postalCode(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    /** The case-insensitive identity of a place within a zone. */
    record Key(String district, String city, String postalCode) {

        static Key of(String district, String city, String postalCode) {
            return new Key(lower(Places.text(district)), lower(Places.text(city)),
                    lower(Places.postalCode(postalCode)));
        }

        private static String lower(String value) {
            return value == null ? null : value.toLowerCase(Locale.ROOT);
        }
    }
}

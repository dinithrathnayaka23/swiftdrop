package com.swiftdrop.notification.service;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock SMS gateway service for SwiftDrop parcel notifications.
 *
 * <ul>
 *   <li>Validates phone number format before simulating dispatch.</li>
 *   <li>Never logs personal data (recipient phone numbers or raw message bodies with PII).</li>
 *   <li>Throws {@link IllegalArgumentException} for malformed phone numbers so the caller can mark
 *       the notification log as {@code FAILED} in the database.</li>
 * </ul>
 */
@Slf4j
@Service
public class SmsService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]{7,25}$");

    /**
     * Dispatches a mock SMS message for the given parcel tracking reference.
     *
     * @param toPhone        recipient phone number (never logged)
     * @param message        SMS payload text
     * @param trackingNumber parcel tracking number for safe diagnostic logging
     */
    public void send(String toPhone, String message, String trackingNumber) {
        if (toPhone == null || toPhone.isBlank() || !PHONE_PATTERN.matcher(toPhone.trim()).matches()) {
            throw new IllegalArgumentException("Invalid recipient phone number format for SMS notification");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("SMS notification message body must not be blank");
        }

        String safeRef = (trackingNumber != null && !trackingNumber.isBlank()) ? trackingNumber : "SYSTEM";
        log.info("[Mock SMS] Dispatched SMS alert for reference {} ({} chars)", safeRef, message.length());
    }
}

package com.swiftdrop.identity.dto.response;

/**
 * Account created; a confirmation link was emailed. No session until the email is confirmed.
 *
 * @param email the address the link was sent to (as stored: trimmed, lower case)
 */
public record CustomerRegistrationResponse(String email) {
}

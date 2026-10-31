package com.swiftdrop.identity.service;

import com.swiftdrop.user.dto.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Short SHA-256 fingerprint of a stored password hash. Put inside reset and refresh tokens so they
 * stop working as soon as the password changes. The hash itself never leaves the server.
 * Google-only accounts (no password) are fingerprinted by their Google subject instead.
 */
final class PasswordFingerprint {

    private PasswordFingerprint() {
    }

    static String of(UserAccount user) {
        return of(user.hasPassword() ? user.passwordHash() : "google:" + user.googleSubject());
    }

    static String of(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

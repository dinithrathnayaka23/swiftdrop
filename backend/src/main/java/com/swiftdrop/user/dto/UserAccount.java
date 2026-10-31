package com.swiftdrop.user.dto;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;
import java.util.UUID;

/**
 * Read-only view of an account for other modules (identity, merchant). Never returned
 * from a controller: it contains the password hash.
 *
 * @param passwordHash  BCrypt hash, or {@code null} for Google-only accounts
 * @param googleSubject Google account id ("sub"), or {@code null} if Google sign-in was never used
 * @param emailVerified whether the user has proven they own {@code email}
 */
public record UserAccount(
        UUID id,
        String fullName,
        String email,
        String passwordHash,
        String googleSubject,
        Role role,
        AccountStatus accountStatus,
        boolean emailVerified) {

    public boolean hasPassword() {
        return passwordHash != null;
    }
}

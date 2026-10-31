package com.swiftdrop.user.dto;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;

/**
 * Data needed to create an account. At least one credential is required (DB check
 * {@code ck_users_has_credentials}): a BCrypt {@code passwordHash} and/or a {@code googleSubject}.
 *
 * @param emailVerified whether ownership of {@code email} is already proven (e.g. by Google)
 */
public record NewUser(
        String fullName,
        String email,
        String phone,
        String passwordHash,
        String googleSubject,
        Role role,
        AccountStatus accountStatus,
        boolean emailVerified) {

    /** Email + password account. The email is unverified until the user opens the emailed link. */
    public static NewUser withPassword(String fullName, String email, String phone, String passwordHash,
                                       Role role, AccountStatus status) {
        return new NewUser(fullName, email, phone, passwordHash, null, role, status, false);
    }

    /** Account created through Google sign-in (no password). Google has verified the email. */
    public static NewUser withGoogle(String fullName, String email, String googleSubject, Role role,
                                     AccountStatus status) {
        return new NewUser(fullName, email, null, null, googleSubject, role, status, true);
    }

    /** The same account with its email already verified (e.g. an admin set up by the operator). */
    public NewUser withVerifiedEmail() {
        return new NewUser(fullName, email, phone, passwordHash, googleSubject, role, accountStatus, true);
    }
}

package com.swiftdrop.user.api;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * An account as other modules and the admin's user list see it. Holds no credentials, so it is safe
 * to return from a controller.
 *
 * @param emailVerified whether the user has proven they own {@code email}
 */
public record UserSummary(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        AccountStatus accountStatus,
        boolean emailVerified,
        Instant createdAt) {
}

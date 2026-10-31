package com.swiftdrop.user.dto.response;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.UUID;

/** The signed-in user's own profile (GET /api/v1/users/me). */
public record UserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        AccountStatus accountStatus,
        Instant createdAt) {
}

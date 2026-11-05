package com.swiftdrop.user.dto.request;

import com.swiftdrop.common.security.Role;
import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/admin/users/{userId}/role */
public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
}

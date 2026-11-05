package com.swiftdrop.user.dto.request;

import com.swiftdrop.user.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/admin/users/{userId}/status */
public record ChangeAccountStatusRequest(@NotNull(message = "Status is required") AccountStatus status) {
}

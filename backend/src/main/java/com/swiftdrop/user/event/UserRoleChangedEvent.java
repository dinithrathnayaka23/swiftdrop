package com.swiftdrop.user.event;

import com.swiftdrop.common.security.Role;
import java.util.UUID;

/**
 * An account's role changed. Published inside the change's transaction; listen with
 * {@code @TransactionalEventListener} to act only once it is committed.
 */
public record UserRoleChangedEvent(UUID userId, Role previousRole, Role newRole) {
}

package com.swiftdrop.user.event;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;
import java.util.UUID;

/**
 * An account's status changed (e.g. deactivated, or a merchant approved). Published inside the
 * change's transaction; listen with {@code @TransactionalEventListener} to act only once committed.
 */
public record UserAccountStatusChangedEvent(UUID userId, Role role, AccountStatus previousStatus,
                                            AccountStatus newStatus) {

    /** Whether the user lost the ability to sign in with this change. */
    public boolean accessRevoked() {
        return previousStatus == AccountStatus.ACTIVE && newStatus != AccountStatus.ACTIVE;
    }
}

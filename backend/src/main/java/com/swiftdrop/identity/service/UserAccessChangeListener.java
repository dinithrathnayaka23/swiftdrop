package com.swiftdrop.identity.service;

import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import com.swiftdrop.user.event.UserRoleChangedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends a user's sessions once their role changes or they lose access, so they can't renew a
 * session with what they could do before: they sign in again with the new role, or not at all.
 * <p>
 * An access token already issued stays valid until it expires (15 minutes by default), as after a
 * password reset. If the session store can't be reached the change stays saved and the failure is
 * logged; renewing a session also re-reads the role and status, so they still apply then.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccessChangeListener {

    private final SessionService sessionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleChanged(UserRoleChangedEvent event) {
        signOutEverywhere(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(UserAccountStatusChangedEvent event) {
        if (event.accessRevoked()) {
            signOutEverywhere(event.userId());
        }
    }

    private void signOutEverywhere(UUID userId) {
        try {
            sessionService.signOutEverywhere(userId);
        } catch (RuntimeException ex) {
            log.warn("Could not end the sessions of user {} after an access change", userId, ex);
        }
    }
}

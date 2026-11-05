package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import com.swiftdrop.user.event.UserRoleChangedEvent;
import com.swiftdrop.user.model.AccountStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAccessChangeListenerTest {

    private final UUID userId = UUID.randomUUID();

    @Mock private SessionService sessionService;
    @InjectMocks private UserAccessChangeListener listener;

    @Test
    void aNewRoleEndsEverySession() {
        listener.onRoleChanged(new UserRoleChangedEvent(userId, Role.CUSTOMER, Role.COURIER));

        verify(sessionService).signOutEverywhere(userId);
    }

    @Test
    void losingAccessEndsEverySession() {
        listener.onStatusChanged(new UserAccountStatusChangedEvent(userId, Role.COURIER, AccountStatus.ACTIVE,
                AccountStatus.SUSPENDED));

        verify(sessionService).signOutEverywhere(userId);
    }

    @Test
    void gainingAccessKeepsSessions() {
        listener.onStatusChanged(new UserAccountStatusChangedEvent(userId, Role.MERCHANT,
                AccountStatus.PENDING_APPROVAL, AccountStatus.ACTIVE));

        verifyNoInteractions(sessionService);
    }

    @Test
    void anUnreachableSessionStoreDoesNotUndoTheChange() {
        doThrow(new IllegalStateException("Redis down")).when(sessionService).signOutEverywhere(userId);

        assertThatCode(() -> listener.onRoleChanged(new UserRoleChangedEvent(userId, Role.COURIER, Role.CUSTOMER)))
                .doesNotThrowAnyException();
    }
}

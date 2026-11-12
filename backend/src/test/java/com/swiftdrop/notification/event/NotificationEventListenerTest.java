package com.swiftdrop.notification.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    void delegatesEventToNotificationService() {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                "SD112233445",
                null,
                "PICKED_UP",
                "OUT_FOR_DELIVERY",
                3L,
                UUID.randomUUID(),
                null,
                Instant.now()
        );

        listener.onParcelStatusChanged(event);

        verify(notificationService).processParcelStatusChange(event);
    }
}


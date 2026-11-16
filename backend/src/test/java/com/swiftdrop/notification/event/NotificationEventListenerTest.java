package com.swiftdrop.notification.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class NotificationEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    void delegatesValidEventToNotificationService() {
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

    @Test
    void rejectsMalformedEventToDeadLetterQueue() {
        ParcelStatusChangedEvent malformed = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION,
                null,
                "SD112233445",
                null,
                "PICKED_UP",
                "",
                1L,
                null,
                null,
                Instant.now()
        );

        assertThatThrownBy(() -> listener.onParcelStatusChanged(malformed))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void recordsDeadLetterEventInDatabase() {
        ParcelStatusChangedEvent deadEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                "SD998877665",
                null,
                "OUT_FOR_DELIVERY",
                "FAILED",
                4L,
                UUID.randomUUID(),
                null,
                Instant.now()
        );

        listener.onDeadLetterParcelStatusEvent(deadEvent);

        verify(notificationService).recordDeadLetterFailure(deadEvent);
    }
}

package com.swiftdrop.notification.event;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link ParcelStatusChangedEvent} from {@code swiftdrop.notification.parcel-events}
 * and handles dead-lettered messages on {@code swiftdrop.parcel.events.dlq}.
 *
 * <ul>
 *   <li>Validates event payloads; malformed events throw {@link AmqpRejectAndDontRequeueException}
 *       so RabbitMQ routes them directly to the Dead-Letter Exchange ({@code swiftdrop.parcel.events.dlx}).</li>
 *   <li>Persists a {@code FAILED} notification log entry when a message arrives in the Dead-Letter Queue.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        if (event == null || event.parcelId() == null || event.newStatus() == null || event.newStatus().isBlank()) {
            log.warn("Rejecting malformed ParcelStatusChangedEvent (eventId={}) to Dead-Letter Queue",
                    event != null ? event.eventId() : null);
            throw new AmqpRejectAndDontRequeueException("Malformed ParcelStatusChangedEvent payload");
        }

        log.info("Received parcel status event: parcelId={}, newStatus={}, eventId={}",
                event.parcelId(), event.newStatus(), event.eventId());
        try {
            notificationService.processParcelStatusChange(event);
        } catch (IllegalArgumentException ex) {
            log.warn("Unrecoverable validation failure for parcel status event {}: {}", event.eventId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid notification event payload", ex);
        } catch (Exception ex) {
            log.error("Failed to process notification for status change event {}", event.eventId(), ex);
            throw ex;
        }
    }

    @RabbitListener(queues = RabbitConfig.DEAD_LETTER_QUEUE)
    public void onDeadLetterParcelStatusEvent(ParcelStatusChangedEvent deadEvent) {
        if (deadEvent == null) {
            log.warn("Received empty message in dead-letter queue {}", RabbitConfig.DEAD_LETTER_QUEUE);
            return;
        }
        log.error("Dead-lettered parcel status event received on {}: eventId={}, parcelId={}, status={}",
                RabbitConfig.DEAD_LETTER_QUEUE,
                deadEvent.eventId(),
                deadEvent.parcelId(),
                deadEvent.newStatus());
        try {
            notificationService.recordDeadLetterFailure(deadEvent);
        } catch (Exception ex) {
            log.error("Failed to persist dead-letter notification log for event {}", deadEvent.eventId(), ex);
        }
    }
}
